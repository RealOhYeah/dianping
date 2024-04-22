package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.Blog;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IBlogService extends IService<Blog> {

    /**
     *  查询多个的blog
     * @param current
     * @return
     */
    Result queryHotBlog(Integer current);

    /**
     * 查看单独博客的详情
     * @param id
     * @return
     */
    Result queryBlogById(Long id);
}
