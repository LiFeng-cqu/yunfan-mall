package com.yunfan.mall.coupon.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yunfan.mall.coupon.entity.CouponEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 优惠券信息
 * 
 * @email HeJieLin@yunfan.com
 * @date 2020-05-22 19:35:30
 */
@Mapper
public interface CouponDao extends BaseMapper<CouponEntity> {
	
}
