package com.atguigu.tingshu.album.service;

import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * 腾讯云点播（VOD）服务：声音文件上传、媒体信息查询
 */
public interface VodService {


    /**
     * 上传声音文件到点播
     * <p>
     * 上传时带上任务流，转码/加密交给点播处理，后续「声音播放」章节可以直接用它生成播放签名
     *
     * @param file 音频文件
     * @return mediaFileId-点播文件唯一标识、mediaUrl-播放地址、mediaDuration-时长（秒）、mediaSize-文件大小（字节）、mediaType-媒体类型
     */
    Map<String, Object> uploadTrack(MultipartFile file);


    /**
     * 查询点播媒体信息
     * <p>
     * 查不到（点播还在处理中、fileId 不存在等）返回空Map，调用方自己兜底，不要让它挡住业务流程
     *
     * @param fileId 点播文件唯一标识
     * @return mediaDuration-时长（秒，BigDecimal）、mediaSize-文件大小（字节，Long）
     */
    Map<String, Object> getMediaInfo(String fileId);

}
