package com.yunfan.mall.search.vo;

import lombok.Data;

/**
 * 自然语言检索请求体。
 */
@Data
public class AiSearchRequest {

    /**
     * 用户自然语言查询，例如：想买 5000 元以内的红色华为手机、要现货。
     */
    private String question;

    /**
     * 页码，从 1 开始，默认 1。
     */
    private Integer pageNum = 1;

    /**
     * 是否让大模型额外生成一段导购推荐语（true 时多一次模型调用，更慢更贵）。
     */
    private Boolean summarize = false;
}
