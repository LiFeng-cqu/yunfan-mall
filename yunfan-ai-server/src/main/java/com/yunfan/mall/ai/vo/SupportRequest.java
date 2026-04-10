package com.yunfan.mall.ai.vo;

import lombok.Data;

/**
 * AI 客服对话请求。
 */
@Data
public class SupportRequest {

    /**
     * 用户一句话，例如：我最近有哪些订单？/ 帮我取消订单 20260701xxxx / 退货运费谁承担？
     */
    private String question;
}
