package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.date.LocalDateTimeUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.dto.ScrollResult;
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
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

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

        String blogSetKey = BLOG_LIKED_KEY + blog.getId();
        UserDTO user = UserHolder.getUser();
        //拦截器未拦截对热门笔记的查询，因此threadLocal中没有用户信息，即使用户登录过也会造成空指针
        if (user == null) {
            //如果用户没登录，就不设置isLiked属性
            return;
        }
        Long userId = user.getId();
        Double score = redisTemplate.opsForZSet().score(blogSetKey, userId.toString());
        blog.setIsLike(score != null);
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
        records.forEach((blog) -> {
            this.isBlogLiked(blog);
            this.queryBlogUser(blog);
        });
        return Result.ok(records);
    }

    @Override
    public Result likeBlog(Long id) {
        //得到用户信息
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return Result.fail("用户未登录！");
        }
        //判断是否点赞过
        String blogSetKey = BLOG_LIKED_KEY + id;
        Double score = redisTemplate.opsForZSet().score(blogSetKey, user.getId().toString());
        if (score == null) {
            //否，在数据库中更新点赞数
            boolean isSuccess = update().setSql("liked = liked + 1").eq("id", id).update();
            //如果更新数据库成功再，在redis中添加点赞的用户
            if (isSuccess) {
                redisTemplate.opsForZSet().add(blogSetKey, user.getId().toString(), System.currentTimeMillis());
            }
        } else {
            //是，数据库取消点赞
            boolean isSuccess = update().setSql("liked = liked - 1").eq("id", id).update();
            //redis移除用户
            if (isSuccess) {
                redisTemplate.opsForZSet().remove(blogSetKey, user.getId().toString());
            }
        }
        return Result.ok();
    }

    /**
     * 根据传入的笔记id，返回给该笔记点赞的前五名用户
     * 查询为本条博客点赞的前五名用户 ，使用zrange 0 4
     * 注意：数据库查询时使用的是in （id1， id2），不会按照id的顺序返回用户，需要使用order by field （id，id1，id2）指定返回的顺序
     *
     * @param id 笔记id
     * @return 前五名用户的集合
     */
    @Override
    public Result queryBlogLikes(Long id) {

        String blogSetKey = BLOG_LIKED_KEY + id;
        //从redis查看到前五个点赞的用户的id
        Set<String> top5 = redisTemplate.opsForZSet().range(blogSetKey, 0, 4);
        if (top5 == null || top5.isEmpty()) {
            //若没人点赞，直接返回
            return Result.ok(Collections.emptyList());
        }
        //将string类型的id转化为Long类型的id集合
        List<Long> ids = top5.stream()
                .map(Long::valueOf)
                .collect(Collectors.toList());
        //使用工具类拼接出sql语句的一部分，如 “5,1,2,3”
        String joinStr = StrUtil.join(", ", ids);
        List<UserDTO> users = userService.query()
                .in("id", ids).last("ORDER BY FIELD (id, " + joinStr + ")")
                .list()
                .stream()
                .map((user) -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());
        return Result.ok(users);
    }

    /**
     * 由后端查询到本页的内容、偏移量（即：本次查询中与最小值相同的博客的数量；当做下次查询的偏移量）、博客内容，返回给前端
     * 下一次查询的时候由前端携带上一次查询的最小时间（也是本次的主打时间）、以及本次查询的偏移量
     *
     * @param maxScore 本次查询的最大时间戳
     * @param offset   由前端传来的本次的偏移量
     * @return 返回下一次查询（下一页）的偏移量offset、本次查询的时间戳最小值minTime、查询到的本页的博客内容
     */
    @Override
    public Result queryFollowedBlog(Long maxScore, int offset) {
        //查询登录用户
        Long userId = UserHolder.getUser().getId();
        //该用户对应的redis收件箱
        String key = FEED_KEY + userId;
        Set<ZSetOperations.TypedTuple<String>> typedTuples = redisTemplate.opsForZSet()
                .reverseRangeByScoreWithScores(key, 0, maxScore, offset, 2);
        //非空判断
        if (typedTuples == null || typedTuples.isEmpty()) {
            return Result.ok();
        }

        Long minTime = 0L;
        int os = 1;
        ArrayList<Long> ids = new ArrayList<>(typedTuples.size());

        for (ZSetOperations.TypedTuple<String> typedTuple : typedTuples) {
            ids.add(Long.valueOf(typedTuple.getValue()));
            Long score = typedTuple.getScore().longValue();
            //由于score值是有序递减的，假设当前值就是最小值，若后续遍历出的值不等于假设的值（即：比当前假设的最小值还要小），
            // 重置最小值、偏移量；遍历结束时，没有其他值来覆盖当前值，则此时minTime为最小
            if (minTime.equals(score)) {
                //当前值与最小值相同，偏移量加一
                os++;
            } else {
                //否则，当前值设为最小值，并重置偏移量
                minTime = score;
                os = 1;
            }
        }
        String joinStr = StrUtil.join(",", ids);
        List<Blog> blogList = query().in("id", ids).last("order by field (id, " + joinStr + ")").list();
        for (Blog blog : blogList) {
            queryBlogUser(blog);
            isBlogLiked(blog);
        }
        //创建返回对象，设置属性
        ScrollResult scrollResult = new ScrollResult();
        scrollResult.setList(blogList);
        scrollResult.setOffset(os);
        scrollResult.setMinTime(minTime);

        return Result.ok(scrollResult);
    }

    /**
     * 将新增的笔记推送给所有的粉丝，即在redis中使用sorted set保存的某用户的收件箱（只保存博客id），
     * score值为时间戳，使用滚动翻页完成，分页查询
     *
     * @param blog 要保存和推送的博客
     * @return 本篇博客的id
     */
    @Override
    public Result saveBlog(Blog blog) {
        // 获取登录用户
        Long userId = UserHolder.getUser().getId();
        blog.setUserId(userId);
        // 保存探店博文
        boolean isSuccess = save(blog);

        // 发送到粉丝收件箱
        if (isSuccess) {
            //获取本用户的所用粉丝
            String followsKey = USER_FOLLOWS_KEY + userId;
            Set<String> followsId = redisTemplate.opsForSet().members(followsKey);
            //判空
            if (followsId == null || followsId.isEmpty()) {
                return Result.ok(blog.getId());
            }
            //给所有的粉丝收件箱推送笔记id
            for (String followId : followsId) {
                redisTemplate.opsForZSet().add(FEED_KEY + followId, blog.getId().toString(), System.currentTimeMillis());
            }

        }

        return Result.ok(blog.getId());
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
