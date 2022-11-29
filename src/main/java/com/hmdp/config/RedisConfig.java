package com.hmdp.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author: lsq637
 * @since: 2022-11-29 10:53:45
 * @describe:
 */
@Configuration
public class RedisConfig {

    @Bean
    public RedissonClient redissonClient() {

        Config config = new Config();
        config.useSingleServer().setAddress("redis://169.254.112.100:6379").setPassword("6376");
        return Redisson.create(config);
    }
}
