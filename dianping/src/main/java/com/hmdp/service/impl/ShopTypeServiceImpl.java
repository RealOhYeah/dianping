package com.hmdp.service.impl;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private IShopTypeService typeService;

    /**
     * 查询所有的店铺类型
     *
     * @return
     */
    @Override
    public Result queryList() {

        Long size = stringRedisTemplate.opsForValue().size(CACHE_SHOP_TYPE_KEY);

        if (size == null || size == 0) {

            List<ShopType> typeList = typeService
                    .query().orderByAsc("sort").list();
            stringRedisTemplate.opsForValue().set(CACHE_SHOP_TYPE_KEY, JSONUtil.toJsonStr(typeList),CACHE_SHOP_TYPE_TTL, TimeUnit.MINUTES);

            return Result.ok(typeList);

        }

        //将json串转化为集合对象
        String s = stringRedisTemplate.opsForValue().get(CACHE_SHOP_TYPE_KEY);
        JSONArray jsonArray = JSONUtil.parseArray(s);
        List<ShopType> typeList = jsonArray.toList(ShopType.class);

        //更新时间
        stringRedisTemplate.expire(CACHE_SHOP_TYPE_KEY, CACHE_SHOP_TYPE_TTL, TimeUnit.MINUTES);

//        System.out.println("????????????????????????????????????");
//        System.out.println(typeList);

        return Result.ok(typeList);
    }
}
