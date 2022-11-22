package com.hmdp.entity;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * @author: lsq637
 * @since: 2022-11-21 14:40:58
 * @describe:
 */
@Data
public class RedisData<T> {

    private LocalDateTime expireTime;
    private T data;
}
