package com.yunfan.mall.ai.controller;

import com.yunfan.common.ai.DeepSeekProperties;
import com.yunfan.common.utils.R;
import com.yunfan.mall.ai.agent.SupportAgentService;
import com.yunfan.mall.ai.vo.SupportRequest;
import com.yunfan.mall.ai.vo.SupportResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 售后客服入口。
 * 需登录（LoginInterceptor /ai/**），登录用户来自共享 Session。
 */
@RestController
@RequestMapping("/ai/support")
public class SupportController {

    @Autowired
    private SupportAgentService supportAgentService;

    @Autowired
    private DeepSeekProperties deepSeekProperties;

    @PostMapping
    public R chat(@RequestBody(required = false) SupportRequest request) {
        String question = request == null ? null : request.getQuestion();
        if (!StringUtils.hasText(question)) {
            return R.error(40000, "请提供要咨询的内容 question");
        }
        if (!StringUtils.hasText(deepSeekProperties.getApiKey())) {
            return R.error(50001,
                    "AI 客服未配置 DeepSeek API Key：请设置环境变量 DEEPSEEK_API_KEY，"
                            + "或在配置中心 / 本地配置 yunfan.ai.deepseek.api-key");
        }
        SupportRequest valid = new SupportRequest();
        valid.setQuestion(question.trim());
        SupportResponse response = supportAgentService.chat(valid);
        return R.ok().setData(response);
    }
}
