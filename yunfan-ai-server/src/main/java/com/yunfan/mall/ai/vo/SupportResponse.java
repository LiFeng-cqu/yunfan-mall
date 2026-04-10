package com.yunfan.mall.ai.vo;

import lombok.Data;

import java.util.List;

/**
 * AI 客服对话响应。
 */
@Data
public class SupportResponse {

    /**
     * 客服回答。
     */
    private String answer;

    /**
     * 本轮实际用到的工具（便于前端展示/排查）。
     */
    private List<String> toolsUsed;
}
