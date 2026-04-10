package com.yunfan.mall.ai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisHttpSession;

/**
 * AI 客服服务启动类。
 * 分布式会话识别登录用户（与商城其它服务共享 Redis Session），通过 Feign 编排订单等下游。
 */
@EnableRedisHttpSession
@EnableFeignClients
@EnableDiscoveryClient
@SpringBootApplication
public class YunfanAiServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(YunfanAiServerApplication.class, args);
    }
}
