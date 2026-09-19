package com.atguigu.tingshu.user.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 微信小程序配置，对应 nacos 中 service-user-dev.yaml 的 wx.miniapp 节点：
 * wx:
 *   miniapp:
 *     appid: wxcc651fcbab275e33
 *     secret: xxx
 *     msgDataFormat: JSON
 */
@Configuration
@ConfigurationProperties(prefix = "wx.miniapp")
@Data
public class WxMiniappProperties {

    private String appid;

    private String secret;

    private String msgDataFormat;
}
