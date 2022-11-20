package com.hmdp;

import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.CreditCodeUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

import static com.hmdp.utils.RedisConstants.LOGIN_CODE_KEY;

@SpringBootTest
class HmDianPingApplicationTests {

    @Autowired
    StringRedisTemplate redisTemplate;
    public void setUp() throws Exception {

        System.out.println("dsaf");

    }

    @Test
    public void test1(){

    }

}
