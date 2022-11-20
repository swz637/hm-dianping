package com.hmdp.config;

import com.hmdp.utils.LoginInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * @author: lsq637
 * @since: 2022-11-15 16:34:12
 * @describe: todo 为什么拦截器没有生效，在点击进入“我的”的时候会直接跳转到登录页？
 * 没生效就ThreadLocal没有user信息，在查询的时候会出错
 *
 */
@Configuration
public class MvcConfig implements WebMvcConfigurer {

    @Autowired
    private StringRedisTemplate redisTemplate;
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new LoginInterceptor(redisTemplate)).excludePathPatterns(
                "/user/code",
                "/user/login",
                "/blog/hot",
                "/upload/**",
                "/shop-type/**",
                "/voucher/**",
                "/shop/**"
        );

    }
}
