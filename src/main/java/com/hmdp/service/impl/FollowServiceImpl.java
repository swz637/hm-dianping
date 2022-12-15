package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.UserHolder;
import com.sun.xml.internal.bind.v2.TODO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.SQLTransactionRollbackException;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {

    @Autowired
    StringRedisTemplate redisTemplate;
    @Autowired
    IUserService userService;

    @Override
    public Result isFollowed(Long followId) {

        Long userId = UserHolder.getUser().getId();
        Integer count = query().eq("user_id", userId).eq("follow_user_id", followId).count();

        return Result.ok(count > 0);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    /**
     *添加事务保证操作数据库与redis的原子性// TODO: 2022/12/8 springboot 的事务怎么显式声明回滚
     */
    public Result follow(Long followId, boolean isFollow) {

        Long userId = UserHolder.getUser().getId();
        //以当前用户拼接一个key，保存该用户关注的人
        String key = RedisConstants.USER_FOLLOWS_KEY + followId;
        if (isFollow) {
            //关注
            Follow follow = new Follow();
            follow.setFollowUserId(followId);
            follow.setUserId(userId);
            boolean isSuccess = save(follow);
            if (isSuccess) {
                //在redis的set集合中存入关注用户的id
                redisTemplate.opsForSet().add(key, userId.toString());
            }
        } else {
            //取消关注
            //数据库中删除该条数据
            boolean isSuccess = remove(new QueryWrapper<Follow>()
                    .eq("user_id", userId)
                    .eq("follow_user_id", followId));
            if (isSuccess) {
                //数据库操作成功后，删除redis中该用户关注的人
                redisTemplate.opsForSet().remove(key, followId.toString());
            }
        }
        return Result.ok();
    }

    /**
     * 查询并返回，登录用户与传入id对应用户的共同关注列表
     *
     * @param id 查看的其他用户
     * @return 共同关注的列表
     */
    @Override
    public Result commonFollows(Long id) {

        //获取当前登录的用户
        Long userId = UserHolder.getUser().getId();
        String keyOfThis = RedisConstants.USER_FOLLOWS_KEY + userId;
        //要查看的其他用户对应的key
        String keyOfFriend = RedisConstants.USER_FOLLOWS_KEY + id;

        //在redis查询交集
        Set<String> strId = redisTemplate.opsForSet().intersect(keyOfThis, keyOfFriend);

        if (strId == null || strId.isEmpty()) {
            //说明两者没有交集，返回空集合
            return Result.ok(Collections.emptyList());
        }
        //将string类型的id转换为Long类型
        List<Long> ids = strId.stream()
                .map(Long::valueOf)
                .collect(Collectors.toList());
        //在数据库查询对应id的用户，并将其转换为UserDTO
        List<UserDTO> users = userService.listByIds(ids)
                .stream()
                .map((user -> BeanUtil.copyProperties(user, UserDTO.class)))
                .collect(Collectors.toList());

        return Result.ok(users);
    }
}
