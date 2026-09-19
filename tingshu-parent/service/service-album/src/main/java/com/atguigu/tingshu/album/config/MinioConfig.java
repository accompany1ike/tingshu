package com.atguigu.tingshu.album.config;

import io.minio.MinioClient;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MinIO 客户端配置
 * <p>
 * 只负责构建客户端对象，不做连通性检查：MinIO 不可用时不希望影响整个服务启动，
 * 真正的连接失败会在上传时暴露出来。
 */
@Configuration
@RequiredArgsConstructor
public class MinioConfig {

    private final MinioConstantProperties minioConstantProperties;

    @Bean
    public MinioClient minioClient() {
        return MinioClient.builder()
                .endpoint(minioConstantProperties.getEndpointUrl())
                .credentials(minioConstantProperties.getAccessKey(), minioConstantProperties.getSecretKey())
                .build();
    }
}
