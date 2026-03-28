package com.yunfan.mall.search.service.impl;

import com.alibaba.fastjson.JSON;
import com.yunfan.common.es.SkuEsModel;
import com.yunfan.mall.search.ai.DeepSeekClient;
import com.yunfan.mall.search.ai.NlQueryFilter;
import com.yunfan.mall.search.config.YunfanElasticSearchConfig;
import com.yunfan.mall.search.constant.EsConstant;
import com.yunfan.mall.search.service.AiSearchService;
import com.yunfan.mall.search.service.MallSearchService;
import com.yunfan.mall.search.vo.AiSearchRequest;
import com.yunfan.mall.search.vo.AiSearchResult;
import com.yunfan.mall.search.vo.SearchParam;
import com.yunfan.mall.search.vo.SearchResult;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.terms.ParsedLongTerms;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * 自然语言检索：
 *
 * <pre>
 * 一句话问题
 *   │  ① 大模型抽取 → NlQueryFilter（JSON schema 约束 + 解析失败降级）
 *   ▼
 * 结构化条件（品牌/分类名 → 通过 ES 反查商品索引解析成 ID）
 *   │  ② 桥接既有 MallSearchService.search(SearchParam)，复用聚合检索 / 分页 / 高亮
 *   ▼
 * 检索结果
 *   │  ③ 0 命中自动放宽；可选 ④ 大模型生成导购推荐语
 *   ▼
 * AiSearchResult
 * </pre>
 *
 * <p>兜底设计：大模型输出不可控，任一步骤失败都退回"关键词检索"，保证接口永远能返回结果。</p>
 */
@Slf4j
@Service
public class AiSearchServiceImpl implements AiSearchService {

    @Autowired
    private MallSearchService mallSearchService;

    @Autowired
    private RestHighLevelClient esRestClient;

    @Autowired
    private DeepSeekClient deepSeekClient;

    /** 抽取类任务用的系统提示：只准输出 JSON。 */
    private static final String EXTRACT_SYSTEM =
            "你是一个电商商品检索意图解析器。只输出一个 JSON 对象，不要输出任何其他文字、解释或 Markdown 代码块标记。"
                    + "把用户的自然语言转换成结构化检索条件，无法确定的字段一律置为 null。";

    /** 生成导购推荐语的系统提示。 */
    private static final String GUIDE_SYSTEM =
            "你是云帆商城（一个电商平台）的智能导购助手。根据用户问题和实际检索到的代表商品，"
                    + "用中文写一小段推荐语：先用一句话点明本次命中了什么（数量 / 品牌 / 价格段），"
                    + "再挑最值得推荐的 1~2 件说明理由。全文不超过 100 字。"
                    + "只能引用下方商品里存在的信息，禁止编造商品、价格或优惠活动。";

    @Override
    public AiSearchResult search(AiSearchRequest request) {
        String question = request.getQuestion() == null ? "" : request.getQuestion().trim();
        int pageNum = request.getPageNum() == null || request.getPageNum() < 1 ? 1 : request.getPageNum();

        AiSearchResult result = new AiSearchResult();
        result.setQuestion(question);

        List<String> notes = new ArrayList<>();
        List<String> segments = new ArrayList<>();

        // ① 大模型抽取结构化条件；任何异常都会被吸收并降级为整句关键词检索
        NlQueryFilter filter = parseFilter(question, notes);
        log.info("AI 检索 question={} parsed={}", question, JSON.toJSONString(filter));

        // ② 桥接既有检索链路
        SearchParam param = buildParam(filter, segments);
        SearchResult sr = mallSearchService.search(param);

        // ③ 0 命中且带硬条件时自动放宽（品牌/分类/价格/库存 → 核心词匹配）
        if (isEmptyResult(sr) && isRestricted(param)) {
            SearchResult broadened = mallSearchService.search(broadenParam(filter, question, pageNum));
            if (!isEmptyResult(broadened)) {
                sr = broadened;
                notes.add("精确条件未命中商品，已自动放宽为按关键词匹配。");
            }
        }

        if (sr == null) {
            notes.add("检索服务暂不可用，请确认 Elasticsearch 已启动且商品已上架。");
        }

        result.setAppliedQuery(segments.isEmpty() ? "（未解析出有效检索条件）" : String.join("，", segments));
        result.setDegradedTo(notes.isEmpty() ? null : String.join("；", notes));
        fillResult(result, sr, pageNum);

        // ④ 可选：大模型导购推荐语（独立兜底，失败不影响主结果）
        if (Boolean.TRUE.equals(request.getSummarize()) && sr != null
                && sr.getProduct() != null && !sr.getProduct().isEmpty()) {
            try {
                result.setAnswer(guide(question, sr.getProduct()));
            } catch (Exception e) {
                log.warn("导购语生成失败，忽略：{}", e.getMessage());
            }
        }
        return result;
    }

    // ==================== ① 自然语言 → 结构化条件 ====================

    private NlQueryFilter parseFilter(String question, List<String> notes) {
        try {
            String content = deepSeekClient.chat(EXTRACT_SYSTEM, buildExtractUser(question), 0.1);
            NlQueryFilter filter = JSON.parseObject(cutJson(content), NlQueryFilter.class);
            if (filter == null) {
                throw new IllegalStateException("模型输出不是合法 JSON");
            }
            return filter;
        } catch (Exception e) {
            log.warn("自然语言解析失败，降级整句检索: {}", e.getMessage());
            notes.add("模型解析失败，已按整句关键词检索。");
            NlQueryFilter fallback = new NlQueryFilter();
            fallback.setKeyword(question);
            return fallback;
        }
    }

    private String buildExtractUser(String question) {
        StringBuilder schema = new StringBuilder();
        schema.append("JSON 字段说明：\n");
        schema.append("- keyword：商品核心类型/型号（如\"手机\"\"Mate60\"），只放\"是什么东西\"，不要放品牌/颜色/价格；给不出则 null。\n");
        schema.append("- brand：品牌名或 null，如\"华为\"。\n");
        schema.append("- category：商品三级分类名（如\"手机\"\"笔记本电脑\"）或 null。\n");
        schema.append("- priceMin / priceMax：人民币价格下限/上限（数字）或 null，如\"5000以内\" => priceMax:5000。\n");
        schema.append("- inStock：明确要求现货/有货时 true，否则 null。\n");
        schema.append("- sort：default | priceAsc | priceDesc | saleDesc 四选一（\"便宜点\"=>priceAsc）。\n");
        schema.append("- spec：颜色/容量/尺寸等无法归入上面的规格描述，多个词用空格分隔；没有则 null。\n");
        schema.append("示例：\"想买5000元以内的红色华为手机，要现货\" => ")
              .append("{\"keyword\":\"手机\",\"brand\":\"华为\",\"category\":null,")
              .append("\"priceMin\":null,\"priceMax\":5000,\"inStock\":true,")
              .append("\"sort\":\"default\",\"spec\":\"红色\"}\n\n");
        schema.append("用户语句：").append(question);
        return schema.toString();
    }

    /** 掐掉模型可能输出的围栏/前后缀，只留最外层 JSON 花括号里的内容。 */
    private String cutJson(String text) {
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        return text;
    }

    // ==================== ② 结构化条件 → SearchParam ====================

    private SearchParam buildParam(NlQueryFilter f, List<String> segments) {
        SearchParam p = new SearchParam();
        List<String> words = new ArrayList<>();

        if (StringUtils.hasText(f.getKeyword())) {
            words.add(f.getKeyword().trim());
            segments.add("关键词：" + f.getKeyword().trim());
        }

        // 品牌名 → brandId：先精确反查商品索引；查不到则退化为标题关键词匹配
        if (StringUtils.hasText(f.getBrand())) {
            String brand = f.getBrand().trim();
            Long brandId = resolveIdByName(brand, "brandName", "brandId");
            if (brandId != null) {
                p.setBrandId(Collections.singletonList(brandId));
                segments.add("品牌：" + brand);
            } else {
                words.add(brand);
                segments.add("品牌：" + brand + "（未在商品数据中精确命中，并入关键词）");
            }
        }

        // 分类名 → catalog3Id，同上
        if (StringUtils.hasText(f.getCategory())) {
            String category = f.getCategory().trim();
            Long catalogId = resolveIdByName(category, "catalogName", "catalogId");
            if (catalogId != null) {
                p.setCatalog3Id(catalogId);
                segments.add("分类：" + category);
            } else {
                words.add(category);
                segments.add("分类：" + category + "（未在商品数据中精确命中，并入关键词）");
            }
        }

        // 剩余规格并入关键词
        if (StringUtils.hasText(f.getSpec())) {
            Collections.addAll(words, f.getSpec().trim().split("\\s+"));
        }

        // 库存：只有明确要求现货才过滤
        if (Boolean.TRUE.equals(f.getInStock())) {
            p.setHasStock(1);
            segments.add("仅看现货");
        }

        // 价格区间：skuPrice = "min_max"
        Double min = f.getPriceMin();
        Double max = f.getPriceMax();
        if (min != null && min < 0) {
            min = null;
        }
        if (max != null && max < 0) {
            max = null;
        }
        if (min != null && max != null) {
            if (min > max) {
                Double tmp = min;
                min = max;
                max = tmp;
            }
            p.setSkuPrice(min.longValue() + "_" + max.longValue());
            segments.add("价格：" + min.longValue() + "~" + max.longValue() + " 元");
        } else if (min != null) {
            p.setSkuPrice(min.longValue() + "_");
            segments.add("价格 ≥ " + min.longValue() + " 元");
        } else if (max != null) {
            p.setSkuPrice("_" + max.longValue());
            segments.add("价格 ≤ " + max.longValue() + " 元");
        }

        // 排序
        if (StringUtils.hasText(f.getSort())) {
            String sort = f.getSort().trim().toLowerCase(Locale.ROOT);
            if ("priceAsc".equals(sort)) {
                p.setSort("skuPrice_asc");
                segments.add("价格从低到高");
            } else if ("priceDesc".equals(sort)) {
                p.setSort("skuPrice_desc");
                segments.add("价格从高到低");
            } else if ("saleDesc".equals(sort)) {
                p.setSort("saleCount_desc");
                segments.add("按销量");
            }
        }

        p.setKeyword(words.isEmpty() ? null : String.join(" ", words));
        return p;
    }

    /**
     * 0 命中时的放宽条件：去掉品牌/分类/价格/库存等硬过滤，只保留"是什么 + 是谁"的标题词，
     * 匹配失败也不会抛异常。
     */
    private SearchParam broadenParam(NlQueryFilter f, String question, int pageNum) {
        SearchParam p = new SearchParam();
        p.setPageNum(pageNum);
        List<String> words = new ArrayList<>();
        if (StringUtils.hasText(f.getKeyword())) {
            words.add(f.getKeyword().trim());
        }
        if (StringUtils.hasText(f.getBrand())) {
            words.add(f.getBrand().trim());
        }
        if (StringUtils.hasText(f.getCategory())) {
            words.add(f.getCategory().trim());
        }
        if (StringUtils.hasText(f.getSpec())) {
            Collections.addAll(words, f.getSpec().trim().split("\\s+"));
        }
        if (words.isEmpty() && StringUtils.hasText(question)) {
            words.add(question);
        }
        p.setKeyword(String.join(" ", words));
        return p;
    }

    private boolean isRestricted(SearchParam p) {
        return (p.getBrandId() != null && !p.getBrandId().isEmpty())
                || p.getCatalog3Id() != null
                || p.getHasStock() != null
                || StringUtils.hasText(p.getSkuPrice());
    }

    private boolean isEmptyResult(SearchResult sr) {
        return sr == null || sr.getTotal() == null || sr.getTotal() == 0;
    }

    // ==================== 桥接：品牌/分类名 → ES 中的 ID ====================

    /**
     * 在商品索引里用名字精确反查 id（brandName→brandId / catalogName→catalogId）。
     * 商品索引同一份数据自带名字与 id，无需跨服务查询。查不到返回 null。
     */
    private Long resolveIdByName(String name, String nameField, String idField) {
        SearchSourceBuilder ssb = new SearchSourceBuilder();
        ssb.size(0);
        ssb.query(QueryBuilders.termQuery(nameField, name));
        ssb.aggregation(AggregationBuilders.terms("id_agg").field(idField).size(1));
        SearchRequest request = new SearchRequest(new String[]{EsConstant.PRODUCT_INDEX}, ssb);
        try {
            SearchResponse response = esRestClient.search(request, YunfanElasticSearchConfig.COMMON_OPTIONS);
            ParsedLongTerms agg = response.getAggregations().get("id_agg");
            if (agg != null && agg.getBuckets() != null && !agg.getBuckets().isEmpty()) {
                return agg.getBuckets().get(0).getKeyAsNumber().longValue();
            }
        } catch (IOException e) {
            log.warn("按 {}={} 反查 id 失败: {}", nameField, name, e.getMessage());
        }
        return null;
    }

    // ==================== 结果组装 ====================

    private void fillResult(AiSearchResult result, SearchResult sr, int pageNum) {
        result.setPageNum(pageNum);
        result.setPageSize(EsConstant.PRODUCT_PAGESIZE);
        if (sr == null) {
            result.setTotal(0L);
            result.setTotalPages(0);
            result.setProducts(new ArrayList<>());
            return;
        }
        result.setTotal(sr.getTotal() == null ? 0L : sr.getTotal());
        result.setTotalPages(sr.getTotalPages() == null ? 0 : sr.getTotalPages());
        result.setBrands(sr.getBrands());
        result.setCatalogs(sr.getCatalogs());
        result.setAttrs(sr.getAttrs());

        List<SkuEsModel> products = sr.getProduct();
        if (products != null) {
            // 既有检索命中后会把标题替换成带 <b> 高亮的片段，对 API/移动端不友好，去掉标签还原原文
            for (SkuEsModel product : products) {
                if (product.getSkuTitle() != null) {
                    product.setSkuTitle(product.getSkuTitle().replaceAll("<[^>]+>", ""));
                }
            }
            result.setProducts(products);
        } else {
            result.setProducts(new ArrayList<>());
        }
    }

    // ==================== ④ 可选导购语 ====================

    private String guide(String question, List<SkuEsModel> products) {
        int top = Math.min(products.size(), 5);
        StringBuilder lines = new StringBuilder();
        for (int i = 0; i < top; i++) {
            SkuEsModel m = products.get(i);
            lines.append(i + 1).append(". ")
                 .append(m.getSkuTitle()).append(" | ")
                 .append(m.getSkuPrice()).append(" 元 | ")
                 .append("销量 ").append(m.getSaleCount() == null ? 0 : m.getSaleCount())
                 .append('\n');
        }
        String user = "用户问题：" + question + "\n\n检索到的代表商品：\n" + lines;
        return deepSeekClient.chat(GUIDE_SYSTEM, user, 0.7);
    }
}
