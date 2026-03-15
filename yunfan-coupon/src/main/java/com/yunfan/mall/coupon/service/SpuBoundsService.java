package com.yunfan.mall.coupon.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.yunfan.common.utils.PageUtils;
import com.yunfan.mall.coupon.entity.SpuBoundsEntity;

import java.util.Map;

/**
 * 商品spu积分设置
 *
 * @email HeJieLin@yunfan.com
 * @date 2020-05-22 19:35:30
 */
public interface SpuBoundsService extends IService<SpuBoundsEntity> {

    PageUtils queryPage(Map<String, Object> params);
}

