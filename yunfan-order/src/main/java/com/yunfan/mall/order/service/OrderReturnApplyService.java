package com.yunfan.mall.order.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.yunfan.common.utils.PageUtils;
import com.yunfan.mall.order.entity.OrderReturnApplyEntity;

import java.util.Map;

/**
 * 订单退货申请
 *
 * @email HeJieLin@yunfan.com
 * @date 2020-05-22 19:49:53
 */
public interface OrderReturnApplyService extends IService<OrderReturnApplyEntity> {

    PageUtils queryPage(Map<String, Object> params);
}

