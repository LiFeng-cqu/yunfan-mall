package com.yunfan.common.ai;

import com.alibaba.fastjson.JSONObject;
import lombok.Data;

import java.util.List;

/**
 * 一次 chat/completions 调用的解析结果。
 *
 * <p>多轮工具调用时，必须把 {@link #assistantMessage}（含 tool_calls 的完整助手消息）
 * 原样追加进 messages，再逐个追加对应工具结果，模型才有上下文完成后续调用/总结。</p>
 */
@Data
public class ChatCall {

    /**
     * 助手最终文本；若本轮只发了工具调用可能为 null。
     */
    private String content;

    /**
     * 助手消息原始 JSON（含 role / content / tool_calls），用于原样回填。
     */
    private JSONObject assistantMessage;

    /**
     * 本轮要执行的工具调用；为空表示模型直接回复文本。
     */
    private List<ToolCall> toolCalls;
}
