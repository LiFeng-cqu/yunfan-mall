package com.yunfan.mall.ware.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.yunfan.common.utils.PageUtils;
import com.yunfan.mall.ware.entity.PurchaseDetailEntity;

import java.util.List;
import java.util.Map;

/**
 * 
 *
 * @email HeJieLin@yunfan.com
 * @date 2020-05-22 19:55:33
 */
public interface PurchaseDetailService extends IService<PurchaseDetailEntity> {

    PageUtils queryPage(Map<String, Object> params);

    List<PurchaseDetailEntity> listDetailByPurchaseId(Long id);
}

