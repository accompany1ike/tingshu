package com.atguigu.tingshu;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 评论服务启动类
 * <p>
 * 评论数据全部落在 MongoDB，不用关系库，所以把数据源自动配置排除掉：
 * 不排除的话 MyBatis-Plus 会因为没有 DataSource 直接启动失败
 */
@SpringBootApplication(exclude = DataSourceAutoConfiguration.class)
@EnableDiscoveryClient
@EnableFeignClients
public class ServiceCommentApplication {

    public static void main(String[] args) {
        SpringApplication.run(ServiceCommentApplication.class, args);
    }

}
