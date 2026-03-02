package com.yunfan.mall.product.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.yunfan.common.utils.PageUtils;
import com.yunfan.mall.product.entity.BrandEntity;

import java.util.Map;

/**
 * 品牌
 *
 * @email HeJieLin@yunfan.com
 * @date 2020-05-22 19:00:18
 */
public interface BrandService extends IService<BrandEntity> {

    PageUtils queryPage(Map<String, Object> params);

    void updateDetail(BrandEntity brand);
}

