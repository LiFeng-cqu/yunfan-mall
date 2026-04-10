package com.yunfan.common.ai;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * DeepSeek 大模型 HTTP 客户端（OpenAI 兼容协议，纯 Java 直调，非流式）。
 *
 * <p>共享给各模块使用。只做协议层：文本对话 {@link #chat} 与工具调用 {@link #chatWithTools}。
 * 无业务逻辑；出错抛 {@link IllegalStateException}，由上层决定兜底策略。
 * 用 JDK 自带 HttpURLConnection，不引入 spring-web 依赖，便于放进公共模块。</p>
 *
 * <p>注意：本类与相关 POJO 都<b>不带 Spring 组件注解</b>。需要用的模块在本地配置里显式注册：</p>
 * <pre>
 *   &#64;Bean &#64;ConfigurationProperties(prefix = "yunfan.ai.deepseek")
 *   public DeepSeekProperties deepSeekProperties() { return new DeepSeekProperties(); }
 *
 *   &#64;Bean
 *   public DeepSeekClient deepSeekClient(DeepSeekProperties p) { return new DeepSeekClient(p); }
 * </pre>
 */
@Slf4j
public class DeepSeekClient {

    private final DeepSeekProperties properties;

    public DeepSeekClient(DeepSeekProperties properties) {
        this.properties = properties;
    }

    /**
     * 最简单形态：系统 + 用户两段文本，返回模型回复正文。
     */
    public String chat(String system, String user, double temperature) {
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(message("system", system));
        messages.add(message("user", user));
        ChatCall call = chatWithTools(messages, null, temperature);
        if (call.getContent() == null) {
            throw new IllegalStateException("DeepSeek 未返回文本内容");
        }
        return call.getContent();
    }

    /**
     * 完整形态：携带历史 messages（可含工具往返）与 tools 定义，返回本轮解析结果。
     *
     * @param messages    messages 数组，每项为 {role, content, ...}
     * @param tools       tools 数组；为空则退化为普通对话
     * @param temperature 采样温度
     */
    public ChatCall chatWithTools(List<Map<String, Object>> messages,
                                  List<Map<String, Object>> tools,
                                  double temperature) {
        if (isBlank(properties.getApiKey())) {
            throw new IllegalStateException(
                    "DeepSeek API Key 未配置：请设置环境变量 DEEPSEEK_API_KEY，或配置 yunfan.ai.deepseek.api-key");
        }

        String url = properties.getBaseUrl().replaceAll("/+$", "") + "/chat/completions";

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", properties.getModel());
        body.put("messages", messages);
        body.put("temperature", temperature);
        body.put("stream", false);
        body.put("max_tokens", 2000);
        if (tools != null && !tools.isEmpty()) {
            body.put("tools", tools);
        }

        String responseBody = postJson(url, JSON.toJSONString(body));
        return parse(responseBody);
    }

    private String postJson(String url, String jsonBody) {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(properties.getTimeoutSeconds() * 1000);
            conn.setReadTimeout(properties.getTimeoutSeconds() * 1000);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("Authorization", "Bearer " + properties.getApiKey().trim());
            conn.setDoOutput(true);

            byte[] bytes = jsonBody.getBytes(StandardCharsets.UTF_8);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(bytes);
                os.flush();
            }

            int code = conn.getResponseCode();
            InputStream stream = code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream();
            String text = readAll(stream);
            if (code >= 200 && code < 300) {
                return text;
            }
            log.warn("DeepSeek 调用失败 status={} body={}", code, truncate(text, 300));
            throw new IllegalStateException("DeepSeek API 调用失败，HTTP " + code);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            log.warn("DeepSeek 调用异常: {}", e.getMessage());
            throw new IllegalStateException("DeepSeek API 调用异常：" + e.getMessage(), e);
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private String readAll(InputStream in) throws Exception {
        if (in == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        }
        return sb.toString();
    }

    private ChatCall parse(String responseBody) {
        JSONObject json = JSON.parseObject(responseBody);
        if (json == null) {
            throw new IllegalStateException("DeepSeek 响应不是合法 JSON");
        }
        JSONArray choices = json.getJSONArray("choices");
        if (choices == null || choices.isEmpty()) {
            throw new IllegalStateException("DeepSeek 响应缺少 choices");
        }
        JSONObject message = choices.getJSONObject(0).getJSONObject("message");
        if (message == null) {
            throw new IllegalStateException("DeepSeek 响应缺少 message");
        }

        ChatCall call = new ChatCall();
        call.setContent(message.getString("content"));
        call.setAssistantMessage(message);

        List<ToolCall> toolCalls = new ArrayList<>();
        JSONArray calls = message.getJSONArray("tool_calls");
        if (calls != null) {
            for (int i = 0; i < calls.size(); i++) {
                JSONObject tc = calls.getJSONObject(i);
                JSONObject fn = tc.getJSONObject("function");
                ToolCall toolCall = new ToolCall();
                toolCall.setId(tc.getString("id"));
                toolCall.setFunctionName(fn == null ? null : fn.getString("name"));
                toolCall.setArguments(fn == null ? null : fn.getString("arguments"));
                toolCalls.add(toolCall);
            }
        }
        call.setToolCalls(toolCalls);
        return call;
    }

    public static Map<String, Object> message(String role, String content) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("role", role);
        m.put("content", content);
        return m;
    }

    private static boolean isBlank(String text) {
        return text == null || text.trim().isEmpty();
    }

    private String truncate(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max) + "...";
    }
}
