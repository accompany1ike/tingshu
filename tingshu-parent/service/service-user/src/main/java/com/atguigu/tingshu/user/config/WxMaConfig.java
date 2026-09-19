package com.atguigu.tingshu.user.config;

import cn.binarywang.wx.miniapp.api.WxMaService;
import cn.binarywang.wx.miniapp.api.impl.WxMaServiceImpl;
import cn.binarywang.wx.miniapp.config.impl.WxMaDefaultConfigImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 微信小程序 SDK（WxJava）客户端配置
 * <p>
 * 这里只负责构建 WxMaService，不做任何网络调用；
 * 微信接口调用失败会在登录时以明确的错误码反馈出来
 */
@Configuration
@RequiredArgsConstructor
public class WxMaConfig {

    private final WxMiniappProperties wxMiniappProperties;

    @Bean
    public WxMaService wxMaService() {
        WxMaDefaultConfigImpl wxMaConfig = new WxMaDefaultConfigImpl();
        wxMaConfig.setAppid(wxMiniappProperties.getAppid());
        wxMaConfig.setSecret(wxMiniappProperties.getSecret());
        wxMaConfig.setMsgDataFormat(wxMiniappProperties.getMsgDataFormat());

        WxMaService wxMaService = new WxMaServiceImpl();
        wxMaService.setWxMaConfig(wxMaConfig);
        return wxMaService;
    }
}
