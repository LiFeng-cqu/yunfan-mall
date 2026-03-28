package com.yunfan.mall.search.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * DeepSeek 大模型调用配置。
 *
 * <p>密钥不写死、不进代码库，通过环境变量或 Nacos / 本地配置注入：</p>
 * <pre>
 *   # 环境变量方式（推荐）
 *   DEEPSEEK_API_KEY=sk-xxxx
 *
 *   # 或 application.yml
 *   yunfan:
 *     ai:
 *       deepseek:
 *         api-key: ${DEEPSEEK_API_KEY:}
 * </pre>
 */
@Data
@Component
@ConfigurationProperties(prefix = "yunfan.ai.deepseek")
public class DeepSeekProperties {

    /**
     * DeepSeek 接口地址（兼容 OpenAI 协议），默认官方地址。
     */
    private String baseUrl = "https://api.deepseek.com";

    /**
     * API Key，默认空，必须通过外部配置注入。
     */
    private String apiKey = "";

    /**
     * 使用的模型，默认 deepseek-chat。
     */
    private String model = "deepseek-chat";

    /**
     * HTTP 连接 / 读取超时（秒）。
     */
    private int timeoutSeconds = 30;
}
