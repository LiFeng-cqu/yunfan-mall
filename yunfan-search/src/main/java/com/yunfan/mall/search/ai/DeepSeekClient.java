package com.yunfan.mall.search.ai;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.yunfan.mall.search.config.DeepSeekProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * DeepSeek 大模型 HTTP 客户端（OpenAI 兼容协议，纯 Java 直调，非流式）。
 *
 * <p>只做一件事：把 system / user 两段文本发给模型，返回回复文本。
 * 不做任何业务逻辑；出错抛 {@link IllegalStateException}，由上层决定兜底策略。</p>
 */
@Slf4j
@Component
public class DeepSeekClient {

    private final DeepSeekProperties properties;
    private final RestTemplate restTemplate;

    public DeepSeekClient(DeepSeekProperties properties) {
        this.properties = properties;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.getTimeoutSeconds() * 1000);
        factory.setReadTimeout(properties.getTimeoutSeconds() * 1000);
        this.restTemplate = new RestTemplate(factory);
    }

    /**
     * 调用 chat 模型，返回消息内容文本。
     *
     * @param system      系统提示词
     * @param user        用户消息
     * @param temperature 采样温度（抽取类任务用 0.1 附近，生成类任务用 0.7 附近）
     * @return 模型回复正文
     * @throws IllegalStateException 未配置 Key / 调用失败 / 响应格式异常
     */
    public String chat(String system, String user, double temperature) {
        if (!StringUtils.hasText(properties.getApiKey())) {
            throw new IllegalStateException(
                    "DeepSeek API Key 未配置：请设置环境变量 DEEPSEEK_API_KEY，或配置 yunfan.ai.deepseek.api-key");
        }
        String url = properties.getBaseUrl().replaceAll("/+$", "") + "/chat/completions";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(properties.getApiKey().trim());

        Map<String, Object> message = new LinkedHashMap<>();
        message.put("role", "user");
        message.put("content", user);

        Map<String, Object> systemMessage = new LinkedHashMap<>();
        systemMessage.put("role", "system");
        systemMessage.put("content", system);

        JSONArray messages = new JSONArray();
        messages.add(systemMessage);
        messages.add(message);

        JSONObject body = new JSONObject();
        body.put("model", properties.getModel());
        body.put("messages", messages);
        body.put("temperature", temperature);
        body.put("stream", false);
        body.put("max_tokens", 1024);

        HttpEntity<String> entity = new HttpEntity<>(body.toJSONString(), headers);

        String responseBody;
        try {
            responseBody = restTemplate.postForObject(url, entity, String.class);
        } catch (RestClientResponseException e) {
            log.warn("DeepSeek 调用失败 status={} body={}", e.getRawStatusCode(),
                    truncate(e.getResponseBodyAsString(), 300));
            throw new IllegalStateException("DeepSeek API 调用失败，HTTP " + e.getRawStatusCode(), e);
        }

        if (!StringUtils.hasText(responseBody)) {
            throw new IllegalStateException("DeepSeek 返回为空");
        }

        String content = parseContent(responseBody);
        if (content == null) {
            log.warn("DeepSeek 响应缺少 content: {}", truncate(responseBody, 300));
            throw new IllegalStateException("DeepSeek 响应缺少 content 字段");
        }
        return content.trim();
    }

    /**
     * 从 chat/completions 响应里取出 choices[0].message.content。
     */
    private String parseContent(String responseBody) {
        JSONObject json = JSONObject.parseObject(responseBody);
        if (json == null) {
            return null;
        }
        JSONArray choices = json.getJSONArray("choices");
        if (choices == null || choices.isEmpty()) {
            return null;
        }
        JSONObject message = choices.getJSONObject(0).getJSONObject("message");
        return message == null ? null : message.getString("content");
    }

    private String truncate(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max) + "...";
    }
}
