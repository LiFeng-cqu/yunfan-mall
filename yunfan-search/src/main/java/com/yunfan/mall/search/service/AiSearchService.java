package com.yunfan.mall.search.service;

import com.yunfan.mall.search.vo.AiSearchRequest;
import com.yunfan.mall.search.vo.AiSearchResult;

/**
 * 自然语言商品检索：{@code 一句话 → 结构化条件 → ES 检索 → (可选)导购语}。
 */
public interface AiSearchService {

    AiSearchResult search(AiSearchRequest request);
}
