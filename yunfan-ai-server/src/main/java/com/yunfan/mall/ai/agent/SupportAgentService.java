package com.yunfan.mall.ai.agent;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.TypeReference;
import com.yunfan.common.ai.ChatCall;
import com.yunfan.common.ai.DeepSeekClient;
import com.yunfan.common.ai.ToolCall;
import com.yunfan.common.ai.ToolSpec;
import com.yunfan.common.utils.R;
import com.yunfan.common.vo.MemberResponseVo;
import com.yunfan.mall.ai.feign.OrderFeignService;
import com.yunfan.mall.ai.interceptor.LoginInterceptor;
import com.yunfan.mall.ai.support.PolicyKnowledgeBase;
import com.yunfan.mall.ai.vo.SupportRequest;
import com.yunfan.mall.ai.vo.SupportResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * AI 售后客服 Agent（function calling 编排）。
 *
 * <p>流程：多轮历史(Redis) + 用户问题 → DeepSeek(function calling)
 * → 模型决定调用哪个工具 → 本地执行（Feign 查/取消订单、政策知识库）→ 结果回填 → 模型总结。
 *
 * <p>边界设计：
 * <ul>
 *   <li>订单归属由【服务端会话】决定——工具不带 memberId，靠透传给 order 的会话 Cookie 识别本人，模型无法越权查他人订单；</li>
 *   <li>取消是写操作：只在模型拿到明确订单号后触发，且 order 侧仍会二次校验状态为"待付款"；</li>
 *   <li>工具执行抛错不外泄，统一转成可向用户转述的兜底文案。</li>
 * </ul>
 */
@Slf4j
@Service
public class SupportAgentService {

    private static final String HISTORY_PREFIX = "ai:chat:";
    private static final int MAX_ROUNDS = 3;
    private static final int HISTORY_TTL_MINUTES = 30;
    private static final int HISTORY_KEEP = 12;

    private static final String SYSTEM_PROMPT =
            "你是云帆商城（自营电商平台）的 AI 售后客服小助手，帮用户处理订单咨询与售后问题。请遵守：\n"
                    + "1. 关于订单的事实（订单号/状态/金额/时间/商品）只能来自工具返回的真实信息，绝不编造。\n"
                    + "2. 用户问售后政策（退货退款、发货时效、运费、发票、售后、取消订单、人工客服）时调用 policy_ask 拿到知识库文案再转述。\n"
                    + "3. 用户问自己的订单时：有订单号就调 order_detail；没有订单号先调 list_my_orders，再把列表展示给用户确认是哪一笔。\n"
                    + "4. cancel_pending_order 只能取消【待付款】订单且是写操作——必须先向用户确认订单号并征得同意，再调用；结果（成功或失败原因）如实转达。\n"
                    + "5. 工具调用失败就如实告诉用户失败原因，并给出下一步建议（如转人工客服）。\n"
                    + "6. 用简体中文、简洁口语、有温度地回复，必要时主动给出后续建议。";

    @Autowired
    private DeepSeekClient deepSeekClient;

    @Autowired
    private OrderFeignService orderFeignService;

    @Autowired
    private PolicyKnowledgeBase knowledgeBase;

    @Autowired
    private StringRedisTemplate redisTemplate;

    public SupportResponse chat(SupportRequest request) {
        MemberResponseVo member = LoginInterceptor.loginUser.get();
        String question = request.getQuestion() == null ? "" : request.getQuestion().trim();

        SupportResponse response = new SupportResponse();
        List<String> toolsUsed = new ArrayList<>();
        response.setToolsUsed(toolsUsed);

        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(DeepSeekClient.message("system", buildSystem(member)));
        messages.addAll(loadHistory(member.getId()));
        messages.add(DeepSeekClient.message("user", question));

        List<Map<String, Object>> tools = toolJsons();

        String answer = null;
        for (int round = 0; round < MAX_ROUNDS; round++) {
            ChatCall call = deepSeekClient.chatWithTools(messages, tools, 0.3);

            if (call.getToolCalls() == null || call.getToolCalls().isEmpty()) {
                // 模型直接给出最终答复
                answer = call.getContent();
                break;
            }

            // 回填助手消息（含 tool_calls），再逐个执行工具
            messages.add(call.getAssistantMessage());
            for (ToolCall toolCall : call.getToolCalls()) {
                String name = toolCall.getFunctionName();
                String result = executeTool(name, toolCall.getArguments());
                toolsUsed.add(name);
                log.info("AI 客服工具执行: {} args={}", name, toolCall.getArguments());
                Map<String, Object> toolMsg = new LinkedHashMap<>();
                toolMsg.put("role", "tool");
                toolMsg.put("tool_call_id", toolCall.getId());
                toolMsg.put("content", result);
                messages.add(toolMsg);
            }
        }

        if (!StringUtils.hasText(answer)) {
            answer = "抱歉，我这边暂时没能完成你的请求，建议转人工客服进一步处理（400-000-0000）。";
        }

        saveHistory(member.getId(), question, answer);
        response.setAnswer(answer);
        return response;
    }

    private String buildSystem(MemberResponseVo member) {
        String who = (StringUtils.hasText(member.getNickname()) ? member.getNickname() : member.getUsername());
        return SYSTEM_PROMPT + "\n当前会话用户：" + who + "（id=" + member.getId() + "）。只可为其本人订单服务。";
    }

    // ==================== 工具执行 ====================

    private String executeTool(String name, String arguments) {
        switch (name == null ? "" : name) {
            case "list_my_orders":
                return listMyOrders();
            case "order_detail": {
                String orderSn = argString(arguments, "orderSn");
                return StringUtils.hasText(orderSn) ? orderDetail(orderSn) : "缺少订单号参数 orderSn。";
            }
            case "cancel_pending_order": {
                String orderSn = argString(arguments, "orderSn");
                return StringUtils.hasText(orderSn) ? cancelOrder(orderSn) : "缺少订单号参数 orderSn。";
            }
            case "policy_ask":
                return knowledgeBase.answer(argString(arguments, "topic"));
            default:
                return "暂不支持该操作，请转人工客服。";
        }
    }

    private String listMyOrders() {
        try {
            R r = orderFeignService.myOrders();
            if (r == null || r.getCode() == null || r.getCode() != 0) {
                return "订单查询失败：" + (r == null ? "无响应" : r.get("msg"));
            }
            List<JSONObject> orders = r.getData("data", new TypeReference<List<JSONObject>>() {
            });
            if (orders == null || orders.isEmpty()) {
                return "当前账号暂时没有订单。";
            }
            StringBuilder sb = new StringBuilder("该账号最近的订单：\n");
            for (JSONObject o : orders) {
                sb.append("- 订单号 ").append(str(o, "orderSn"))
                        .append("｜状态：").append(str(o, "statusMsg"))
                        .append("｜金额 ¥").append(str(o, "totalAmount"))
                        .append("｜下单 ").append(str(o, "createTime"))
                        .append("｜商品：").append(namesOf(o)).append('\n');
            }
            return sb.toString();
        } catch (Exception e) {
            log.error("list_my_orders 失败", e);
            return "订单服务暂时不可用，请稍后重试或转人工客服。";
        }
    }

    private String orderDetail(String orderSn) {
        try {
            R r = orderFeignService.orderDetail(orderSn);
            if (r == null || r.getCode() == null || r.getCode() != 0) {
                return "订单查询失败：" + (r == null ? "无响应" : r.get("msg"));
            }
            JSONObject o = r.getData("data", new TypeReference<JSONObject>() {
            });
            if (o == null) {
                return "未找到订单 " + orderSn;
            }
            StringBuilder sb = new StringBuilder();
            sb.append("订单 ").append(str(o, "orderSn")).append('\n');
            sb.append("状态：").append(str(o, "statusMsg")).append('\n');
            sb.append("实付金额：¥").append(str(o, "payAmount")).append("（运费 ¥")
                    .append(str(o, "freightAmount")).append("）\n");
            sb.append("下单时间：").append(str(o, "createTime")).append('\n');
            sb.append("商品明细：\n");
            JSONArray items = o.getJSONArray("items");
            if (items != null) {
                for (int i = 0; i < items.size(); i++) {
                    JSONObject item = items.getJSONObject(i);
                    sb.append("- ").append(str(item, "skuName"))
                            .append(" ×").append(str(item, "skuQuantity"))
                            .append("，单价 ¥").append(str(item, "skuPrice"));
                    if (StringUtils.hasText(str(item, "skuAttrsVals"))) {
                        sb.append("（").append(str(item, "skuAttrsVals")).append("）");
                    }
                    sb.append('\n');
                }
            }
            return sb.toString();
        } catch (Exception e) {
            log.error("order_detail 失败 orderSn={}", orderSn, e);
            return "订单服务暂时不可用，请稍后重试或转人工客服。";
        }
    }

    private String cancelOrder(String orderSn) {
        try {
            R r = orderFeignService.cancelOrder(orderSn);
            Object msg = r == null ? null : r.get("msg");
            if (r != null && r.getCode() != null && r.getCode() == 0) {
                return "取消成功：" + msg;
            }
            return msg == null ? "取消失败，请稍后重试。" : msg.toString();
        } catch (Exception e) {
            log.error("cancel_pending_order 失败 orderSn={}", orderSn, e);
            return "订单服务暂时不可用，取消操作未生效，请稍后重试或转人工客服。";
        }
    }

    // ==================== 工具参数解析与文本工具 ====================

    private String argString(String arguments, String key) {
        if (!StringUtils.hasText(arguments)) {
            return null;
        }
        try {
            JSONObject args = JSON.parseObject(arguments);
            return args == null ? null : args.getString(key);
        } catch (Exception e) {
            return null;
        }
    }

    private String str(JSONObject o, String key) {
        Object v = o.get(key);
        return v == null ? "" : v.toString();
    }

    private String namesOf(JSONObject o) {
        Object v = o.get("itemNames");
        if (v instanceof JSONArray) {
            JSONArray arr = (JSONArray) v;
            List<String> names = new ArrayList<>();
            for (int i = 0; i < arr.size(); i++) {
                names.add(String.valueOf(arr.get(i)));
            }
            return String.join("、", names);
        }
        return v == null ? "" : v.toString();
    }

    // ==================== 工具定义 ====================

    private List<Map<String, Object>> toolJsons() {
        List<ToolSpec> specs = new ArrayList<>();
        specs.add(new ToolSpec("list_my_orders",
                "查询当前登录会员最近的订单列表（含订单号、订单状态、金额、下单时间、主要商品）。用户问“我的订单/我买了什么/下了几单/订单到哪了”时先调用。",
                objectParams()));

        Map<String, Object> orderSn = new LinkedHashMap<>();
        orderSn.put("type", "string");
        orderSn.put("description", "完整的订单号，例如 2026070112345678");
        Map<String, Object> orderSnParams = new LinkedHashMap<>();
        orderSnParams.put("type", "object");
        orderSnParams.put("properties", singleProp("orderSn", orderSn));
        orderSnParams.put("required", java.util.Collections.singletonList("orderSn"));

        specs.add(new ToolSpec("order_detail",
                "查询某笔订单的详细状态、金额与商品明细。必须先从用户消息或订单列表中获得完整订单号 orderSn。",
                orderSnParams));
        specs.add(new ToolSpec("cancel_pending_order",
                "取消一笔【待付款】状态的订单（仅待付款可取消，写操作）。仅当用户明确表示要取消某笔订单、并已确认订单号后调用。",
                orderSnParams));
        specs.add(new ToolSpec("policy_ask",
                "查询云帆商城售后政策（退货退款、发货时效、运费、发票、售后服务、取消订单、人工客服），根据用户问题把 topic 传成最相关主题词。",
                topicParams()));

        List<Map<String, Object>> tools = new ArrayList<>();
        for (ToolSpec spec : specs) {
            tools.add(spec.toJson());
        }
        return tools;
    }

    private Map<String, Object> objectParams() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("type", "object");
        params.put("properties", new LinkedHashMap<>());
        return params;
    }

    /** 构造形如 {"name": 属性Schema} 的单个属性 */
    private Map<String, Object> singleProp(String name, Map<String, Object> propSchema) {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put(name, propSchema);
        return props;
    }

    private Map<String, Object> topicParams() {
        Map<String, Object> topic = new LinkedHashMap<>();
        topic.put("type", "string");
        topic.put("description", "售后政策主题");
        topic.put("enum", knowledgeBase.topics());
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("type", "object");
        params.put("properties", singleProp("topic", topic));
        params.put("required", java.util.Collections.singletonList("topic"));
        return params;
    }

    // ==================== 会话记忆（Redis，就近几轮） ====================

    private List<Map<String, Object>> loadHistory(Long memberId) {
        List<Map<String, Object>> history = new ArrayList<>();
        String key = HISTORY_PREFIX + memberId;
        try {
            String json = redisTemplate.opsForValue().get(key);
            if (StringUtils.hasText(json)) {
                JSONArray arr = JSON.parseArray(json);
                if (arr != null) {
                    for (int i = 0; i < arr.size(); i++) {
                        history.add(arr.getJSONObject(i));
                    }
                }
            }
        } catch (Exception e) {
            log.warn("读取 AI 客服历史失败 key={}", key, e);
        }
        return history;
    }

    private void saveHistory(Long memberId, String question, String answer) {
        List<Map<String, Object>> history = loadHistory(memberId);
        history.add(DeepSeekClient.message("user", question));
        history.add(DeepSeekClient.message("assistant", answer));
        while (history.size() > HISTORY_KEEP) {
            history.remove(0);
        }
        try {
            redisTemplate.opsForValue().set(HISTORY_PREFIX + memberId, JSON.toJSONString(history),
                    HISTORY_TTL_MINUTES, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.warn("保存 AI 客服历史失败 memberId={}", memberId, e);
        }
    }
}
