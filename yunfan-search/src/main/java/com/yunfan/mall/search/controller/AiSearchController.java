package com.yunfan.mall.search.controller;

import com.yunfan.common.ai.DeepSeekProperties;
import com.yunfan.mall.search.service.AiSearchService;
import com.yunfan.mall.search.vo.AiSearchRequest;
import com.yunfan.common.utils.R;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 自然语言商品检索入口。
 *
 * <p>用一句日常话就能搜商品，例如：</p>
 * <pre>
 *   POST /ai/search   {"question":"5000元以内的红色华为手机，要现货","summarize":true}
 *   GET  /ai/search?question=小米笔记本电脑&pageNum=1
 * </pre>
 */
@Slf4j
@RestController
@RequestMapping("/ai/search")
public class AiSearchController {

    @Autowired
    private AiSearchService aiSearchService;

    @Autowired
    private DeepSeekProperties deepSeekProperties;

    @PostMapping
    public R search(@RequestBody(required = false) AiSearchRequest request) {
        String question = request == null ? null : request.getQuestion();
        if (!StringUtils.hasText(question)) {
            return R.error(40000, "请提供自然语言查询内容 question");
        }
        return doSearch(question, request.getPageNum(), Boolean.TRUE.equals(request.getSummarize()));
    }

    @GetMapping
    public R search(@RequestParam("question") String question,
                    @RequestParam(value = "pageNum", required = false) Integer pageNum,
                    @RequestParam(value = "summarize", defaultValue = "false") boolean summarize) {
        if (!StringUtils.hasText(question)) {
            return R.error(40000, "请提供自然语言查询内容 question");
        }
        return doSearch(question, pageNum, summarize);
    }

    private R doSearch(String question, Integer pageNum, boolean summarize) {
        if (!StringUtils.hasText(deepSeekProperties.getApiKey())) {
            return R.error(50001,
                    "AI 检索未配置 DeepSeek API Key：请设置环境变量 DEEPSEEK_API_KEY，"
                            + "或在配置中心 / 本地配置 yunfan.ai.deepseek.api-key");
        }
        AiSearchRequest request = new AiSearchRequest();
        request.setQuestion(question.trim());
        request.setPageNum(pageNum == null ? 1 : pageNum);
        request.setSummarize(summarize);
        return R.ok().setData(aiSearchService.search(request));
    }
}
