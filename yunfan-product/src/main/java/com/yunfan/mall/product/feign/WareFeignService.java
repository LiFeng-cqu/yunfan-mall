package com.yunfan.mall.product.feign;

import com.yunfan.common.utils.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

/**
 * @Description:
 * @Created: with IntelliJ IDEA.
 * @createTime: 2020-06-06 15:50
 **/

@FeignClient("yunfan-ware")
public interface WareFeignService {

    @PostMapping(value = "/ware/waresku/hasStock")
    R getSkuHasStock(@RequestBody List<Long> skuIds);

}
