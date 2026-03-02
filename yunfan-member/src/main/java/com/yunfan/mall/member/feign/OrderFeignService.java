package com.yunfan.mall.member.feign;

import com.yunfan.common.utils.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

/**
 * @Description:
 * @Created: with IntelliJ IDEA.
 * @createTime: 2020-07-08 15:34
 **/

@FeignClient("yunfan-order")
public interface OrderFeignService {

    /**
     * 分页查询当前登录用户的所有订单信息
     * @param params
     * @return
     */
    @PostMapping("/order/order/listWithItem")
    R listWithItem(@RequestBody Map<String, Object> params);

}
