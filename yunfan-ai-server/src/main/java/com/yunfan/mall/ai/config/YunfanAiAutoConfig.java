package com.yunfan.mall.ai.config;

import com.yunfan.common.ai.DeepSeekClient;
import com.yunfan.common.ai.DeepSeekProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 注册共享的 DeepSeek 客户端 Bean（客户端类在 yunfan-common，本模块显式装配）。
 * 配置前缀 yunfan.ai.deepseek，Key 走环境变量 DEEPSEEK_API_KEY，不落库。
 */
@Configuration
public class YunfanAiAutoConfig {

    @Bean
    @ConfigurationProperties(prefix = "yunfan.ai.deepseek")
    public DeepSeekProperties deepSeekProperties() {
        return new DeepSeekProperties();
    }

    @Bean
    public DeepSeekClient deepSeekClient(DeepSeekProperties deepSeekProperties) {
        return new DeepSeekClient(deepSeekProperties);
    }
}
