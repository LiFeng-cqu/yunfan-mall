package com.yunfan.mall.search.service;

import com.yunfan.mall.search.vo.SearchParam;
import com.yunfan.mall.search.vo.SearchResult;

/**
 * @Description:
 * @Created: with IntelliJ IDEA.
 * @createTime: 2020-06-13 14:17
 **/
public interface MallSearchService {

    /**
     * @param param 检索的所有参数
     * @return  返回检索的结果，里面包含页面需要的所有信息
     */
    SearchResult search(SearchParam param);
}
