package com.yunfan.mall.product.dao;

import com.yunfan.mall.product.entity.CategoryEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 商品三级分类
 * 
 * @email HeJieLin@yunfan.com
 * @date 2020-05-22 19:00:18
 */
@Mapper
public interface CategoryDao extends BaseMapper<CategoryEntity> {
	
}
