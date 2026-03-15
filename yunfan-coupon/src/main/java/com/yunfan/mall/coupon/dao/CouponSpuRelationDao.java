package com.yunfan.mall.coupon.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yunfan.mall.coupon.entity.CouponSpuRelationEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 优惠券与产品关联
 * 
 * @email HeJieLin@yunfan.com
 * @date 2020-05-22 19:35:30
 */
@Mapper
public interface CouponSpuRelationDao extends BaseMapper<CouponSpuRelationEntity> {
	
}
