package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.Follow;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 * 服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IFollowService extends IService<Follow> {

    /**
     * 查询登录用户是否关注过传入的用户
     *
     * @param followId 传入的用户id
     * @return 是否关注
     */
    Result isFollowed(Long followId);

    /**
     * 根据传入的参数，关注或取关传入的用户
     *
     * @param followId 传入的用户
     * @param isFollow 关注 true 取关 false
     * @return 操作结果
     */
    Result follow(Long followId, boolean isFollow);

    Result commonFollows(Long id);
}
