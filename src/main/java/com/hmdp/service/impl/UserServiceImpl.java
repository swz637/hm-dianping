package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.CreditCodeUtil;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import com.sun.org.apache.bcel.internal.generic.NEW;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;

import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import static com.hmdp.utils.RedisConstants.*;
import static com.hmdp.utils.SystemConstants.*;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Autowired
    StringRedisTemplate redisTemplate;

    @Override
    public Result sendCode(String phone, HttpSession session) {
        //校验手机号是否合规
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号错误！请重新输入！");
        }
        //生成验证码
        String code = RandomUtil.randomNumbers(6);
        //保存验证码及其对应的手机号到redis
        redisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone, code, Duration.ofMinutes(LOGIN_CODE_TTL));
        //发送验证码
        log.info("验证码：" + code);

        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {

        if (loginForm == null) {
            return Result.fail("没有收到验证信息！");
        }
        if (loginForm.getCode() != null) {
            return loginByCode(loginForm, session);
        } else {
            return loginByPassword(loginForm, session);
        }

    }

    private Result loginByPassword(LoginFormDTO loginForm, HttpSession session) {

        String phone = loginForm.getPhone();
        String password = loginForm.getPassword();
        //CreditCodeUtil.
        return Result.ok();
    }

    public Result loginByCode(LoginFormDTO loginForm, HttpSession session) {

        String phone = loginForm.getPhone();
        //校验手机号与验证码是否和redis中的一致
        String code = redisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);
        if (!loginForm.getCode().equals(code)) {
            return Result.fail("验证码错误！");
        }
        //根据手机号查用户
        User user = query().eq("phone", phone).one();
        //如果没有该用户，则创建用户，保存到数据库
        if (user == null) {
            user = createUserByPhone(phone);
        }
        //保存用户简略信息（UserDTO）到redis
        //将user类对象的属性拷贝到UserDTO
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        //将userDTO转换为hashmap，使用hash类型保存到redis
        //由于使用的是StringRedisTemplate，要求key和value都是string类型，因此需要将value转换为字符串类型
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(), CopyOptions.create()
                .setIgnoreNullValue(true)
                .setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString()));
        //生成随机token
        String token = UUID.randomUUID().toString(true);

        redisTemplate.opsForHash().putAll(LOGIN_USER_KEY + token, userMap);
        //一定要设置过期时间，防止redis中长期存有很久没登录的用户
        redisTemplate.expire(LOGIN_USER_KEY + token, Duration.ofMinutes(LOGIN_USER_TTL));
        return Result.ok(token);
    }

    private User createUserByPhone(String phone) {

        User user = new User();
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(6));
        //保存到数据库
        save(user);
        return user;
    }
}
