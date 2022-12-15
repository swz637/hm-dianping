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

    /**根据id从数据库查笔记
     * @param blogId 笔记的id
     * @return 返回笔记的对象
     */
    Result queryById(Long blogId);


    /**查询热门的笔记
     * @param current ？
     * @return 查询到的热门笔记列表
     */
    Result queryHotBlog(Integer current);

    /**给对应的笔记点赞
     * @param id 笔记id
     * @return 结果
     */
    Result likeBlog(Long id);

    Result queryBlogLikes(Long id);

    Result queryFollowedBlog(Long maxScore, int offset);

    Result saveBlog(Blog blog);
}
