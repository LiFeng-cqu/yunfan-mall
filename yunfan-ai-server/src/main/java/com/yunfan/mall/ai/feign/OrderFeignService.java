package com.yunfan.mall.ai.feign;

import com.yunfan.common.utils.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 调用订单服务的 AI 专用内部端点。
 * 会话 Cookie 由 GuliFeignConfig 透传，下游据此识别当前登录会员（无需传 memberId）。
 */
@FeignClient("yunfan-order")
public interface OrderFeignService {

    @GetMapping("/order/order/ai/orders")
    R myOrders();

    @GetMapping("/order/order/ai/detail")
    R orderDetail(@RequestParam("orderSn") String orderSn);

    @PostMapping("/order/order/ai/cancel")
    R cancelOrder(@RequestParam("orderSn") String orderSn);
}
