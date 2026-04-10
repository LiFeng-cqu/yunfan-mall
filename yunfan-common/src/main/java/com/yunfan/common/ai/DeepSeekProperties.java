package com.yunfan.common.ai;

import lombok.Data;

/**
 * DeepSeek 大模型调用配置（共享给各消费模块）。
 *
 * <p>明文 POJO，不注解任何 Spring 组件注解；由各消费模块在本地配置里显式注册并绑定
 * {@code yunfan.ai.deepseek.*} 前缀（如 {@code @Bean @ConfigurationProperties(prefix="yunfan.ai.deepseek")}）。</p>
 *
 * <p>密钥不写死、不进代码库，通过环境变量或配置中心注入，例如：</p>
 * <pre>
 *   DEEPSEEK_API_KEY=sk-xxxx
 * </pre>
 */
@Data
public class DeepSeekProperties {

    /**
     * DeepSeek 接口地址（OpenAI 兼容协议），默认官方地址。
     */
    private String baseUrl = "https://api.deepseek.com";

    /**
     * API Key，默认空，必须由外部注入。
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
