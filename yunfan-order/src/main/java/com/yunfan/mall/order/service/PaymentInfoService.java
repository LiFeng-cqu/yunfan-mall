package com.yunfan.mall.order.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.yunfan.common.utils.PageUtils;
import com.yunfan.mall.order.entity.PaymentInfoEntity;

import java.util.Map;

/**
 * 支付信息表
 *
 * @email HeJieLin@yunfan.com
 * @date 2020-05-22 19:49:53
 */
public interface PaymentInfoService extends IService<PaymentInfoEntity> {

    PageUtils queryPage(Map<String, Object> params);
}

