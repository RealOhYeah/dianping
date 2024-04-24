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

    /**
     * 点赞逻辑
     * @param id
     * @return
     */
    Result likeBlog(Long id);

    /**
     * 点赞排行榜
     * @param id
     * @return
     */
    Result queryBlogLikes(Long id);

    /**
     * 发布笔记
     * @param blog
     * @return
     */
    Result saveBlog(Blog blog);

    /**
     * 实现分页查询收邮箱
     * @param max
     * @param offset
     * @return
     */
    Result queryBlogOfFollow(Long max, Integer offset);
}
