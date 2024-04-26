package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IShopService extends IService<Shop> {

    /**
     * 根据id查询商铺信息
     * @param id 商铺id
     * @return 商铺详情数据
     */
    Result queryById(Long id);

    /**
     * 更新商铺信息
     * @param shop 商铺数据
     * @return 无
     */
    Result update(Shop shop);

    /**
     * 根据商铺类型分页查询商铺信息
     * @param typeId 商铺类型id
     * @param current 当前页码
     * @param x 纬度
     * @param y 经度
     * @return 商铺列表
     */
    Result queryShopByType(Integer typeId, Integer current, Double x, Double y);
}
