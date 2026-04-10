package com.yunfan.mall.ai.interceptor;

import com.alibaba.fastjson.JSON;
import com.yunfan.common.utils.R;
import com.yunfan.common.vo.MemberResponseVo;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

import static com.yunfan.common.constant.AuthServerConstant.LOGIN_USER;

/**
 * AI 客服登录拦截器：从共享 Session 读当前登录会员，放进 ThreadLocal。
 * 未登录直接返回 JSON（区别于网页端的 HTML 跳转，这里是纯 API）。
 */
@Component
public class LoginInterceptor implements HandlerInterceptor {

    public static final ThreadLocal<MemberResponseVo> loginUser = new ThreadLocal<>();

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        MemberResponseVo member = (MemberResponseVo) request.getSession().getAttribute(LOGIN_USER);
        if (member == null) {
            writeJson(response, R.error(50011, "请先登录后再使用 AI 客服（未登录无法查询个人订单）"));
            return false;
        }
        loginUser.set(member);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        // 清理，避免线程复用串号
        loginUser.remove();
    }

    private void writeJson(HttpServletResponse response, R r) throws Exception {
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType("application/json;charset=UTF-8");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        PrintWriter out = response.getWriter();
        out.write(JSON.toJSONString(r));
        out.flush();
    }
}
