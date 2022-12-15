package com.hmdp.utils;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.swing.text.DateFormatter;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * @author: lsq637
 * @since: 2022-11-22 14:32:11
 * @describe: 使用redis完成各业务的id自增需求
 */
@Component
public class IDWorker {

    @Autowired
    private StringRedisTemplate redisTemplate;
    //将1998.10.4 00:00:00设置为起始时间
    private static final long START_TIMESTAMP = 907459200L;
    //序列号的位数
    private static final int COUNT_BITS = 32;

    public long getId(String keyPrefix) {

        LocalDateTime now = LocalDateTime.now();
        long nowEpochSecond = now.toEpochSecond(ZoneOffset.UTC);
        long timeStamp = nowEpochSecond - START_TIMESTAMP;
        String date = now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        //通过redis自增获得后面32位的序列号
        // TODO: 2022/11/26 此处的count会在redis中永久存在，怎么解决？ 应该过一天就进行持久化保存到硬盘？方便以后根据key统计当天订单
        // TODO: 2022/11/29 redis怎么持久化？
        Long count = redisTemplate.opsForValue().increment("icr:" + keyPrefix + ":" + date, 1L);

        //通过位运算将时间戳向左位移32位，空出的32位使用或运算将count填充
        return timeStamp << COUNT_BITS | count;
    }
}
