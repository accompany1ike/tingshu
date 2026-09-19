package com.atguigu.tingshu.album.service.impl;

import com.atguigu.tingshu.album.config.VodConstantProperties;
import com.atguigu.tingshu.album.service.VodService;
import com.atguigu.tingshu.common.execption.GuiguException;
import com.atguigu.tingshu.common.result.ResultCodeEnum;
import com.qcloud.vod.VodUploadClient;
import com.qcloud.vod.model.VodUploadRequest;
import com.qcloud.vod.model.VodUploadResponse;
import com.tencentcloudapi.common.Credential;
import com.tencentcloudapi.vod.v20180717.VodClient;
import com.tencentcloudapi.vod.v20180717.models.DescribeMediaInfosRequest;
import com.tencentcloudapi.vod.v20180717.models.DescribeMediaInfosResponse;
import com.tencentcloudapi.vod.v20180717.models.MediaInfo;
import com.tencentcloudapi.vod.v20180717.models.MediaMetaData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * 腾讯云点播业务实现：本地临时文件 -> 上传点播 -> 返回文件标识与播放地址
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VodServiceImpl implements VodService {

    /**
     * 单个音频大小上限，与 Nacos 里 spring.servlet.multipart.max-file-size 保持一致
     */
    private static final long MAX_TRACK_SIZE = 100 * 1024 * 1024;

    /**
     * 上传刚结束就去查媒体信息可能还查不到（点播正在解析），最多重试几次
     */
    private static final int MEDIA_INFO_RETRY_TIMES = 3;

    private static final long MEDIA_INFO_RETRY_INTERVAL = 500L;

    private final VodConstantProperties vodProperties;

    @Override
    public Map<String, Object> uploadTrack(MultipartFile file) {
        //1.基础校验：空文件、超大文件直接拒绝
        if (file == null || file.isEmpty()) {
            throw new GuiguException(ResultCodeEnum.ILLEGAL_REQUEST.getCode(), "请选择要上传的音频文件");
        }
        if (file.getSize() > MAX_TRACK_SIZE) {
            throw new GuiguException(ResultCodeEnum.ILLEGAL_REQUEST.getCode(),
                    "音频大小不能超过" + (MAX_TRACK_SIZE / 1024 / 1024) + "MB");
        }

        String originalFilename = StringUtils.hasText(file.getOriginalFilename())
                ? file.getOriginalFilename() : "track.mp3";
        Path tempFile = null;
        try {
            //2.点播SDK只认本地文件路径，先把上传的文件落到临时目录（目录不存在时自动创建）
            String tempPath = StringUtils.hasText(vodProperties.getTempPath())
                    ? vodProperties.getTempPath() : System.getProperty("java.io.tmpdir");
            Path tempDir = Paths.get(tempPath);
            Files.createDirectories(tempDir);
            tempFile = tempDir.resolve(UUID.randomUUID().toString().replace("-", "")
                    + "." + this.getSuffix(originalFilename, "mp3"));
            file.transferTo(tempFile.toFile());

            //3.上传到点播：过程失败（密钥错误、欠费、网络不通）时点播会返回具体原因，原样带出去方便定位
            VodUploadRequest uploadRequest = new VodUploadRequest();
            uploadRequest.setMediaFilePath(tempFile.toString());
            uploadRequest.setMediaName(originalFilename);
            if (StringUtils.hasText(vodProperties.getProcedure())) {
                uploadRequest.setProcedure(vodProperties.getProcedure());
            }
            VodUploadClient uploadClient = new VodUploadClient(vodProperties.getSecretId(), vodProperties.getSecretKey());
            VodUploadResponse uploadResponse = uploadClient.upload(vodProperties.getRegion(), uploadRequest);

            String fileId = uploadResponse.getFileId();
            String mediaUrl = uploadResponse.getMediaUrl();
            if (!StringUtils.hasText(fileId) || !StringUtils.hasText(mediaUrl)) {
                throw new GuiguException(ResultCodeEnum.FAIL.getCode(), "上传到点播失败，请稍后重试");
            }

            //4.组装返回结果：时长、大小点播里查不到时用兜底值，不能因为查不到就让上传失败
            Map<String, Object> mediaInfo = this.getMediaInfoWithRetry(fileId);
            String mediaType = this.getSuffix(originalFilename, null);
            if (!StringUtils.hasText(mediaType)) {
                mediaType = this.getSuffix(mediaUrl, "");
            }

            Map<String, Object> result = new HashMap<>(8);
            result.put("mediaFileId", fileId);
            result.put("mediaUrl", mediaUrl);
            result.put("mediaType", mediaType);
            result.put("mediaSize", mediaInfo.getOrDefault("mediaSize", file.getSize()));
            result.put("mediaDuration", mediaInfo.getOrDefault("mediaDuration", BigDecimal.ZERO));
            log.info("声音上传成功，fileId：{}，地址：{}，大小：{}，时长：{}", fileId, mediaUrl,
                    result.get("mediaSize"), result.get("mediaDuration"));
            return result;
        } catch (GuiguException e) {
            throw e;
        } catch (Exception e) {
            log.error("声音上传失败，原文件名：{}", originalFilename, e);
            throw new GuiguException(ResultCodeEnum.FAIL.getCode(), "声音上传失败：" + e.getMessage());
        } finally {
            //5.不管成功失败都要清掉临时文件，避免把磁盘占满
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException e) {
                    log.warn("临时文件删除失败：{}", tempFile, e);
                }
            }
        }
    }

    @Override
    public Map<String, Object> getMediaInfo(String fileId) {
        if (!StringUtils.hasText(fileId)) {
            return new HashMap<>();
        }
        try {
            VodClient vodClient = new VodClient(
                    new Credential(vodProperties.getSecretId(), vodProperties.getSecretKey()),
                    vodProperties.getRegion());
            DescribeMediaInfosRequest request = new DescribeMediaInfosRequest();
            request.setFileIds(new String[]{fileId});
            DescribeMediaInfosResponse response = vodClient.DescribeMediaInfos(request);

            MediaInfo[] mediaInfoSet = response.getMediaInfoSet();
            if (mediaInfoSet == null || mediaInfoSet.length == 0) {
                log.warn("点播里查询不到该媒体，fileId：{}", fileId);
                return new HashMap<>();
            }
            MediaMetaData metaData = mediaInfoSet[0].getMetaData();
            if (metaData == null) {
                return new HashMap<>();
            }

            Map<String, Object> mediaInfo = new HashMap<>(4);
            if (metaData.getDuration() != null) {
                mediaInfo.put("mediaDuration", BigDecimal.valueOf(metaData.getDuration())
                        .setScale(2, RoundingMode.HALF_UP));
            }
            if (metaData.getSize() != null) {
                mediaInfo.put("mediaSize", metaData.getSize());
            }
            return mediaInfo;
        } catch (Exception e) {
            log.warn("查询点播媒体信息失败，fileId：{}", fileId, e);
            return new HashMap<>();
        }
    }

    /**
     * 带重试的媒体信息查询：上传刚结束时点播可能还在解析，等一会儿再查
     *
     * @param fileId 点播文件唯一标识
     * @return 媒体信息，多次都查不到返回空Map
     */
    private Map<String, Object> getMediaInfoWithRetry(String fileId) {
        for (int i = 0; i < MEDIA_INFO_RETRY_TIMES; i++) {
            Map<String, Object> mediaInfo = this.getMediaInfo(fileId);
            if (!mediaInfo.isEmpty()) {
                return mediaInfo;
            }
            try {
                Thread.sleep(MEDIA_INFO_RETRY_INTERVAL);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        log.warn("点播媒体信息暂未就绪，fileId：{}，先按默认值保存", fileId);
        return new HashMap<>();
    }

    /**
     * 取文件后缀（媒体类型）
     * <p>
     * track_info.media_type 只有10个字符，超长的后缀一律丢弃
     *
     * @param nameOrUrl    文件名或地址
     * @param defaultValue 取不到时的默认值，可以为 null
     * @return 小写后缀，例如 mp3
     */
    private String getSuffix(String nameOrUrl, String defaultValue) {
        if (!StringUtils.hasText(nameOrUrl)) {
            return defaultValue;
        }
        String value = nameOrUrl;
        int queryIndex = value.indexOf('?');
        if (queryIndex > -1) {
            value = value.substring(0, queryIndex);
        }
        String suffix = StringUtils.getFilenameExtension(value);
        suffix = suffix == null ? null : suffix.toLowerCase(Locale.ROOT);
        if (!StringUtils.hasText(suffix) || suffix.length() > 10) {
            return defaultValue;
        }
        return suffix;
    }
}
