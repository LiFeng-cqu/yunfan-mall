package com.yunfan.mall.product.feign;

import com.yunfan.common.es.SkuEsModel;
import com.yunfan.common.utils.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

/**
 * @Description:
 * @Created: with IntelliJ IDEA.
 * @createTime: 2020-06-06 17:12
 **/

@FeignClient("yunfan-search")
public interface SearchFeignService {

    @PostMapping(value = "/search/save/product")
    public R productStatusUp(@RequestBody List<SkuEsModel> skuEsModels);

}
