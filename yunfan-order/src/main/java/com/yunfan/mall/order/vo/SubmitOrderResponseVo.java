package com.yunfan.mall.order.vo;

import com.yunfan.mall.order.entity.OrderEntity;
import lombok.Data;

/**
 * @Description:
 * @Created: with IntelliJ IDEA.
 * @createTime: 2020-07-04 22:34
 **/

@Data
public class SubmitOrderResponseVo {

    private OrderEntity order;

    /** 错误状态码 **/
    private Integer code;


}
