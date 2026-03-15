package com.yunfan.mall.coupon.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.yunfan.common.to.SkuReductionTo;
import com.yunfan.common.utils.PageUtils;
import com.yunfan.mall.coupon.entity.SkuFullReductionEntity;

import java.util.Map;

/**
 * 商品满减信息
 *
 * @email HeJieLin@yunfan.com
 * @date 2020-05-22 19:35:30
 */
public interface SkuFullReductionService extends IService<SkuFullReductionEntity> {

    PageUtils queryPage(Map<String, Object> params);

    void saveSkuReduction(SkuReductionTo skuReductionTo);
}

