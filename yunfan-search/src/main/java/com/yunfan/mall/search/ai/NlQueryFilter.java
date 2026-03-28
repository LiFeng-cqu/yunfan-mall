package com.yunfan.mall.search.ai;

import lombok.Data;

/**
 * 大模型从一句自然语言购物语句里抽取出的结构化检索条件。
 *
 * <p>字段与给模型的 JSON schema 一一对应，模型输出经 fastjson 直接反序列化。
 * 模型答不出的字段保持 null，后续组装检索条件时按 null 跳过即可。</p>
 */
@Data
public class NlQueryFilter {

    /**
     * 商品核心类型 / 型号，如：手机、Mate60。已剔除品牌、分类、价格等词。
     */
    private String keyword;

    /**
     * 品牌名，如：华为；不确定时为 null。
     */
    private String brand;

    /**
     * 商品三级分类名，如：手机、笔记本电脑；不确定时为 null。
     */
    private String category;

    /**
     * 价格下限（人民币元），未限定时为 null。
     */
    private Double priceMin;

    /**
     * 价格上限（人民币元），未限定时为 null。
     */
    private Double priceMax;

    /**
     * 用户明确要求现货 / 有货时为 true，否则为 null。
     */
    private Boolean inStock;

    /**
     * 排序意图：default / priceAsc / priceDesc / saleDesc。
     */
    private String sort;

    /**
     * 无法归入以上字段的规格描述（颜色、容量、尺寸…），可为 null。
     */
    private String spec;
}
