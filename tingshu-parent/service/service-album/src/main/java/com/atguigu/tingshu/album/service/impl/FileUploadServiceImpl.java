package com.atguigu.tingshu.album.service.impl;

import com.atguigu.tingshu.album.config.MinioConstantProperties;
import com.atguigu.tingshu.album.service.FileUploadService;
import com.atguigu.tingshu.common.execption.GuiguException;
import com.atguigu.tingshu.common.result.ResultCodeEnum;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.SetBucketPolicyArgs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 图片上传业务实现：文件校验 -> 生成对象名 -> 上传 MinIO -> 返回访问地址
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileUploadServiceImpl implements FileUploadService {

    /**
     * 单张图片大小上限：10MB，与 spring.servlet.multipart.max-file-size 保持一致
     */
    private static final long MAX_IMAGE_SIZE = 10 * 1024 * 1024;

    /**
     * 允许的图片格式：以 ImageIO 实际解码结果为准，不信任文件名后缀和 Content-Type
     */
    private static final Set<String> ALLOWED_FORMATS = Set.of("jpeg", "png", "gif", "bmp");

    /**
     * 图片格式 -> 存储后缀
     */
    private static final Map<String, String> FORMAT_SUFFIX = Map.of(
            "jpeg", "jpg",
            "png", "png",
            "gif", "gif",
            "bmp", "bmp");

    /**
     * 图片格式 -> 响应头 Content-Type
     */
    private static final Map<String, String> FORMAT_CONTENT_TYPE = Map.of(
            "jpeg", "image/jpeg",
            "png", "image/png",
            "gif", "image/gif",
            "bmp", "image/bmp");

    /**
     * 匿名只读策略：新建存储桶时使用，保证图片地址能被前端直接访问
     */
    private static final String PUBLIC_READ_POLICY = "{\"Version\":\"2012-10-17\",\"Statement\":[{\"Effect\":\"Allow\","
            + "\"Principal\":{\"AWS\":[\"*\"]},\"Action\":[\"s3:GetObject\"],\"Resource\":[\"arn:aws:s3:::%s/*\"]}]}";

    private final MinioClient minioClient;

    private final MinioConstantProperties minioProperties;

    /**
     * 存储桶是否已就绪，避免每次上传都去查询/创建
     */
    private final AtomicBoolean bucketReady = new AtomicBoolean(false);

    @Override
    public String uploadImage(MultipartFile file) {
        //1.基础校验：空文件、超大文件直接拒绝
        if (file == null || file.isEmpty()) {
            throw new GuiguException(ResultCodeEnum.ILLEGAL_REQUEST.getCode(), "请选择要上传的图片");
        }
        if (file.getSize() > MAX_IMAGE_SIZE) {
            throw new GuiguException(ResultCodeEnum.ILLEGAL_REQUEST.getCode(),
                    "图片大小不能超过" + (MAX_IMAGE_SIZE / 1024 / 1024) + "MB");
        }
        try {
            byte[] content = file.getBytes();

            //2.按文件真实内容识别图片格式：改后缀、伪造 Content-Type 的文件在这里会被拦下
            String format = detectImageFormat(content);
            if (format == null) {
                throw new GuiguException(ResultCodeEnum.ILLEGAL_REQUEST.getCode(), "只支持 jpg、png、gif、bmp 格式的图片");
            }

            //3.生成对象名：yyyy-MM-dd/uuid.后缀（按天分目录，避免单目录文件过多）
            String objectName = LocalDate.now() + "/" + UUID.randomUUID() + "." + FORMAT_SUFFIX.get(format);

            //4.上传：显式指定 content-type，浏览器/小程序可直接预览
            prepareBucket();
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(minioProperties.getBucketName())
                    .object(objectName)
                    .contentType(FORMAT_CONTENT_TYPE.get(format))
                    .stream(new ByteArrayInputStream(content), content.length, -1)
                    .build());

            String url = buildAccessUrl(objectName);
            log.info("图片上传成功，地址：{}，大小：{}KB，原文件名：{}", url, content.length / 1024, file.getOriginalFilename());
            return url;
        } catch (GuiguException e) {
            throw e;
        } catch (Exception e) {
            log.error("图片上传失败，原文件名：{}", file.getOriginalFilename(), e);
            throw new GuiguException(ResultCodeEnum.FAIL.getCode(), "图片上传失败，请稍后重试");
        }
    }

    /**
     * 识别图片真实格式
     *
     * @param content 文件内容
     * @return 允许的格式名称（jpeg/png/gif/bmp），不是图片或格式不支持时返回 null
     */
    private String detectImageFormat(byte[] content) throws IOException {
        try (ImageInputStream imageInputStream = ImageIO.createImageInputStream(new ByteArrayInputStream(content))) {
            if (imageInputStream == null) {
                return null;
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(imageInputStream);
            if (!readers.hasNext()) {
                return null;
            }
            ImageReader reader = readers.next();
            try {
                String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                return ALLOWED_FORMATS.contains(format) ? format : null;
            } finally {
                reader.dispose();
            }
        }
    }

    /**
     * 确保存储桶可用：不存在则创建，并设置匿名只读策略（只执行一次）
     */
    private void prepareBucket() throws Exception {
        if (bucketReady.get()) {
            return;
        }
        synchronized (this) {
            if (bucketReady.get()) {
                return;
            }
            String bucket = minioProperties.getBucketName();
            boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                minioClient.setBucketPolicy(SetBucketPolicyArgs.builder()
                        .bucket(bucket)
                        .config(String.format(PUBLIC_READ_POLICY, bucket))
                        .build());
                log.info("存储桶 {} 不存在，已创建并设置为匿名可读", bucket);
            }
            bucketReady.set(true);
        }
    }

    /**
     * 拼接图片访问地址
     */
    private String buildAccessUrl(String objectName) {
        String prefix = StringUtils.hasText(minioProperties.getPublicUrlPrefix())
                ? minioProperties.getPublicUrlPrefix()
                : minioProperties.getEndpointUrl();
        while (prefix.endsWith("/")) {
            prefix = prefix.substring(0, prefix.length() - 1);
        }
        return prefix + "/" + minioProperties.getBucketName() + "/" + objectName;
    }
}
