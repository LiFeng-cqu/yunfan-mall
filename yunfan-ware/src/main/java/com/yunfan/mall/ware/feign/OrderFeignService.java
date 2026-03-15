package com.yunfan.mall.ware.feign;

import com.yunfan.common.utils.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * @Description:
 * @Created: with IntelliJ IDEA.
 * @createTime: 2020-07-06 23:28
 **/

@FeignClient("yunfan-order")
public interface OrderFeignService {

    @GetMapping(value = "/order/order/status/{orderSn}")
    R getOrderStatus(@PathVariable("orderSn") String orderSn);

}
