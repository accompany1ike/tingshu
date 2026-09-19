package com.atguigu.tingshu.album.config;


import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix="vod") //读取节点
@Data
public class VodConstantProperties {

    /**
     * 点播应用ID：用 Long 而不是 Integer，腾讯云账号里的 ID 可能是12位数字，超过 Integer 上限会导致启动失败
     */
    private Long appId;
    private String secretId;
    private String secretKey;
    //https://cloud.tencent.com/document/api/266/31756#.E5.9C.B0.E5.9F.9F.E5.88.97.E8.A1.A8
    private String region;
    private String procedure;
    private String tempPath;
    private String playKey;
}
