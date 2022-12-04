package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {

    @Resource
    private IUserService userService;

    @Autowired
    private StringRedisTemplate redisTemplate;
    @Override
    public Result queryById(Long blogId) {

        Blog blog = getById(blogId);
        if (blog == null) {
            return Result.fail("笔记不存在！");
        }
        queryBlogUser(blog);
        isBlogLiked(blog);
        return Result.ok(blog);
    }

    private void isBlogLiked(Blog blog) {
        String blogSetKey = "blog:liked:" + blog.getId();
        UserDTO user = UserHolder.getUser();
        //拦截器未拦截对热门笔记的查询，因此threadLocal中没有用户信息，即使用户登录过也会造成空指针
        if (user == null){
            //如果用户没登录，就不设置isLiked属性
            return;
        }
        Long userId = user.getId();
        Boolean isMember = redisTemplate.opsForSet().isMember(blogSetKey, userId.toString());
        blog.setIsLike(BooleanUtil.isTrue(isMember));
    }

    @Override
    public Result queryHotBlog(Integer current) {

        // 根据用户查询
        Page<Blog> page = query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 查询用户，设置blog属性
        //records.forEach(blog ->{
        //    queryBlogUser(blog);
        //})可简化为如下表达式 :records.forEach(this::queryBlogUser);;
        records.forEach((blog)->{
            this.isBlogLiked(blog);
            this.queryBlogUser(blog);
        });
        return Result.ok(records);
    }

    @Override
    public Result likeBlog(Long id) {
        //得到用户信息
        UserDTO user = UserHolder.getUser();
        if (user == null){
            return Result.fail("用户未登录！");
        }
        //判断是否点赞过
        String blogSetKey = "blog:liked:" + id;
        Boolean isMember = redisTemplate.opsForSet().isMember(blogSetKey, user.getId().toString());
        if (BooleanUtil.isFalse(isMember)){
            //否，在数据库中更新点赞数
            boolean isSuccess = update().setSql("liked = liked + 1").eq("id", id).update();
            //如果更新数据库成功再，在redis中添加点赞的用户
            if (isSuccess) {
                redisTemplate.opsForSet().add(blogSetKey, user.getId().toString());
            }
        }else {
            //是，数据库取消点赞
            boolean isSuccess = update().setSql("liked = liked - 1").eq("id", id).update();
            //redis移除用户
            if (isSuccess) {
                redisTemplate.opsForSet().remove(blogSetKey, user.getId().toString());
            }
        }
        return Result.ok();
    }

    /**
     * 根据传入的笔记对象中的用户id，在数据库查询到对应的用户，
     * 将用户的信息设置到blog对象的属性中
     *
     * @param blog 笔记对象
     */
    private void queryBlogUser(Blog blog) {

        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
    }
}
