package com.yunfan.mall.order.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.yunfan.common.utils.R;
import com.yunfan.common.vo.MemberResponseVo;
import com.yunfan.mall.order.entity.OrderEntity;
import com.yunfan.mall.order.entity.OrderItemEntity;
import com.yunfan.mall.order.enume.OrderStatusEnum;
import com.yunfan.mall.order.interceptor.LoginUserInterceptor;
import com.yunfan.mall.order.service.OrderItemService;
import com.yunfan.mall.order.service.OrderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * AI 客服专用内部端点（受 OrderWebConfig 登录拦截器保护，用户身份取自当前会话）。
 *
 * <p>由 yunfan-ai-server 通过 Feign（透传会话 Cookie）调用。
 * 查询/操作都以"当前登录会员"为准，只允许操作本人的订单，防止客服 Agent 越权查他人订单。</p>
 */
@Slf4j
@RestController
@RequestMapping("order/order/ai")
public class OrderAiController {

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderItemService orderItemService;

    /**
     * 当前会员最近的若干订单（含主要商品名），供"我的订单/我买了什么"类问答。
     */
    @GetMapping("/orders")
    public R orders() {
        MemberResponseVo member = currentMember();
        if (member == null) {
            return R.error(50010, "未登录");
        }
        List<OrderEntity> orders = orderService.listRecentOrders(member.getId(), 6);
        List<Map<String, Object>> list = orders.stream().map(this::briefOf).collect(Collectors.toList());
        return R.ok().setData(list);
    }

    /**
     * 按订单号查当前会员某订单详情（含商品明细）。
     */
    @GetMapping("/detail")
    public R detail(@RequestParam("orderSn") String orderSn) {
        MemberResponseVo member = currentMember();
        if (member == null) {
            return R.error(50010, "未登录");
        }
        OrderEntity order = orderService.getOrderByOrderSn(orderSn);
        if (order == null) {
            return R.error(50020, "订单不存在：" + orderSn);
        }
        if (!Objects.equals(order.getMemberId(), member.getId())) {
            log.warn("AI 客服越权访问被拦：memberId={} 尝试查看订单 {} 归属 memberId={}",
                    member.getId(), orderSn, order.getMemberId());
            return R.error(50021, "无权查看他人的订单");
        }
        return R.ok().setData(detailOf(order));
    }

    /**
     * 取消当前会员的一笔待付款订单（写操作，仅供明确取消意图时调用）。
     */
    @PostMapping("/cancel")
    public R cancel(@RequestParam("orderSn") String orderSn) {
        MemberResponseVo member = currentMember();
        if (member == null) {
            return R.error(50010, "未登录");
        }
        OrderEntity order = orderService.getOrderByOrderSn(orderSn);
        if (order == null) {
            return R.error(50020, "订单不存在：" + orderSn);
        }
        if (!Objects.equals(order.getMemberId(), member.getId())) {
            log.warn("AI 客服越权取消被拦：memberId={} 尝试取消订单 {} 归属 memberId={}",
                    member.getId(), orderSn, order.getMemberId());
            return R.error(50021, "无权操作他人的订单");
        }
        if (!Objects.equals(order.getStatus(), OrderStatusEnum.CREATE_NEW.getCode())) {
            return R.error(50022, "该订单当前状态为【" + statusMsg(order.getStatus()) + "】，仅待付款订单支持在线取消");
        }
        // 复用既有关单逻辑：置为已取消并异步释放库存（仅待付款订单生效）
        orderService.closeOrder(order);
        log.info("AI 客服取消订单成功：memberId={} orderSn={}", member.getId(), orderSn);
        return R.ok().put("msg", "订单 " + orderSn + " 已取消");
    }

    // ============ 组装返回（只暴露必要字段，不回传收货人手机等敏感信息） ============

    private Map<String, Object> briefOf(OrderEntity order) {
        Map<String, Object> map = new HashMap<>();
        map.put("orderSn", order.getOrderSn());
        map.put("status", order.getStatus());
        map.put("statusMsg", statusMsg(order.getStatus()));
        map.put("totalAmount", order.getPayAmount());
        map.put("createTime", formatTime(order));
        List<OrderItemEntity> items = itemsOf(order.getOrderSn());
        map.put("itemCount", items.size());
        map.put("itemNames", items.stream().limit(3).map(OrderItemEntity::getSkuName).collect(Collectors.toList()));
        return map;
    }

    private Map<String, Object> detailOf(OrderEntity order) {
        Map<String, Object> map = new HashMap<>();
        map.put("orderSn", order.getOrderSn());
        map.put("status", order.getStatus());
        map.put("statusMsg", statusMsg(order.getStatus()));
        map.put("payAmount", order.getPayAmount());
        map.put("freightAmount", order.getFreightAmount() == null ? BigDecimal.ZERO : order.getFreightAmount());
        map.put("createTime", formatTime(order));
        List<Map<String, Object>> items = itemsOf(order.getOrderSn()).stream().map(item -> {
            Map<String, Object> im = new HashMap<>();
            im.put("skuName", item.getSkuName());
            im.put("skuPrice", item.getSkuPrice());
            im.put("skuQuantity", item.getSkuQuantity());
            im.put("skuAttrsVals", item.getSkuAttrsVals());
            im.put("spuBrand", item.getSpuBrand());
            return im;
        }).collect(Collectors.toList());
        map.put("items", items);
        return map;
    }

    private List<OrderItemEntity> itemsOf(String orderSn) {
        return orderItemService.list(new QueryWrapper<OrderItemEntity>().eq("order_sn", orderSn));
    }

    private MemberResponseVo currentMember() {
        return LoginUserInterceptor.loginUser.get();
    }

    private String statusMsg(Integer status) {
        if (status == null) {
            return "未知";
        }
        for (OrderStatusEnum e : OrderStatusEnum.values()) {
            if (e.getCode().equals(status)) {
                return e.getMsg();
            }
        }
        return String.valueOf(status);
    }

    private String formatTime(OrderEntity order) {
        if (order.getCreateTime() == null) {
            return "";
        }
        return new SimpleDateFormat("yyyy-MM-dd HH:mm").format(order.getCreateTime());
    }
}
