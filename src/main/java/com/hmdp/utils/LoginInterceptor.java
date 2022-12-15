package com.hmdp.utils;

import cn.hutool.core.bean.BeanUtil;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.time.Duration;
import java.util.Map;

import static com.hmdp.utils.RedisConstants.LOGIN_USER_KEY;
import static com.hmdp.utils.RedisConstants.LOGIN_USER_TTL;

/**
 * @author: lsq637
 * @since: 2022-11-15 16:14:10
 * @describe:
 */
public class LoginInterceptor implements HandlerInterceptor {

    private StringRedisTemplate redisTemplate;


    public LoginInterceptor(StringRedisTemplate redisTemplate) {

        this.redisTemplate = redisTemplate;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        //校验用户是否存在
        String token = request.getHeader(SystemConstants.TOKEN_KEY);
        Map<Object, Object> userMap = redisTemplate.opsForHash().entries(LOGIN_USER_KEY + token);
        //不存在，拦截
        if (userMap.isEmpty()) {
            if (request.getRequestURI().contains("blog/hot")){
                //如果未登录，但是请求是查询热门笔记的也放行
                return true;
            }
            //返回错误状态码，表示未授权
            response.setStatus(401);
            return false;
        }
        //将userMap转换为UserDTO对象
        UserDTO user = new UserDTO();
        BeanUtil.fillBeanWithMap(userMap, user, false);
        //存在保存用户到ThreadLocal
        UserHolder.saveUser(user);
        //将用户登录token刷新，即用户每访问一次controller层都会被拦截，从而刷新token
        redisTemplate.expire(LOGIN_USER_KEY + token, Duration.ofMinutes(LOGIN_USER_TTL));
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {

        UserHolder.removeUser();
    }
}
