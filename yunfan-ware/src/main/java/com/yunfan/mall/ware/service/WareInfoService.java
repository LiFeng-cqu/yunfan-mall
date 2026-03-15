package com.yunfan.mall.ware.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.yunfan.common.utils.PageUtils;
import com.yunfan.mall.ware.entity.WareInfoEntity;
import com.yunfan.mall.ware.vo.FareVo;

import java.util.Map;

/**
 * 仓库信息
 *
 * @email HeJieLin@yunfan.com
 * @date 2020-05-22 19:55:33
 */
public interface WareInfoService extends IService<WareInfoEntity> {

    PageUtils queryPage(Map<String, Object> params);

    /**
     * 获取运费和收货地址信息
     * @param addrId
     * @return
     */
    FareVo getFare(Long addrId);
}

