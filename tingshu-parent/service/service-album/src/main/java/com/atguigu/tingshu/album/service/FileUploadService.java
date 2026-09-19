package com.atguigu.tingshu.album.service;

import org.springframework.web.multipart.MultipartFile;

public interface FileUploadService {

    /**
     * 上传图片到对象存储
     *
     * @param file 上传的图片文件
     * @return 可直接访问的图片地址（用于专辑封面、声音封面等）
     */
    String uploadImage(MultipartFile file);
}
