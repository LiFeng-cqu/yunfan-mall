package com.yunfan.mall.search.vo;

import com.yunfan.common.es.SkuEsModel;
import lombok.Data;

import java.util.List;

/**
 * 自然语言检索结果。
 *
 * <p>product 商品命中复用上架 / 网页检索共用的 {@link SkuEsModel}；
 * brands / catalogs / attrs 复用了既有检索的聚合结果（可选侧栏筛选）。
 * answer 为可选的大模型导购推荐语。</p>
 */
@Data
public class AiSearchResult {

    /**
     * 原始提问。
     */
    private String question;

    /**
     * 模型解析后实际生效的检索条件（人类可读），便于排查与建立信任。
     */
    private String appliedQuery;

    /**
     * 非空表示本次发生了降级，说明降级原因（兜底策略，保证接口稳定返回）。
     */
    private String degradedTo;

    private Long total;

    private Integer pageNum;

    private Integer pageSize;

    private Integer totalPages;

    /**
     * 命中的商品（已去除高亮标签，title 为原文）。
     */
    private List<SkuEsModel> products;

    private List<SearchResult.BrandVo> brands;

    private List<SearchResult.CatalogVo> catalogs;

    private List<SearchResult.AttrVo> attrs;

    /**
     * 可选：大模型生成的导购推荐语。
     */
    private String answer;
}
