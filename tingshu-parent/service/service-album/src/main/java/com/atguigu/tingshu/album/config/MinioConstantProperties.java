package com.atguigu.tingshu.album.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix="minio") //读取节点
@Data
public class MinioConstantProperties {

    private String endpointUrl;
    private String accessKey;
    /** 注意：配置项为 minio.secretKey，字段名必须一致，否则绑定不到值 */
    private String secretKey;
    private String bucketName;
    /**
     * 可选：对外可访问的地址前缀（例如网关域名、CDN）。
     * 不配置时使用 endpointUrl，适合 MinIO 地址本身就能被客户端访问的场景
     */
    private String publicUrlPrefix;
}
