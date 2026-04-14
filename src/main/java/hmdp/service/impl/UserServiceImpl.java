package hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import hmdp.dto.LoginFormDTO;
import hmdp.dto.Result;
import hmdp.dto.UserDTO;
import hmdp.entity.User;
import hmdp.mapper.UserMapper;
import hmdp.service.IUserService;
import hmdp.utils.RegexUtils;
import hmdp.utils.UserHolder;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static hmdp.utils.RedisConstants.*;
import static hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result sendCode(String phone, HttpSession session) {
        //1.校验手机号
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式错误！");
        }

        //2.生成验证码
        String code = RandomUtil.randomNumbers(6);

        //3.保存验证码到Redis
        stringRedisTemplate.opsForValue().set(
                LOGIN_CODE_KEY + phone,
                code,
                LOGIN_CODE_TTL,
                TimeUnit.MINUTES
        );

        //4.模拟发送信息
        log.debug("发送短信验证码成功，验证码：{}", code);

        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        //1.检验手机号
        String phone = loginForm.getPhone();
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式错误！");
        }

        //2.从Redis获取验证码并校验
        String cacheCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);
        String code = loginForm.getCode();
        if (cacheCode == null || !cacheCode.equals(code)) {
            return Result.fail("验证码错误！");
        }

        //验证码校验通过后，删除验证码
        stringRedisTemplate.delete(LOGIN_CODE_KEY + phone);

        //3.根据手机号查询用户
        User user = query().eq("phone", phone).one();

        //4.用户不存在，自动注册
        if (user == null) {
            user = createUserWithPhone(phone);
        }

        //5.生成token
        String token = UUID.randomUUID().toString(true);



        //6.将 User 转为 UserDTO
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);

        //7.将UserDTO转为Map
        Map<String, Object> userMap = BeanUtil.beanToMap(
                userDTO,
                new HashMap<>(),
                cn.hutool.core.bean.copier.CopyOptions.create()
                        .setIgnoreNullValue(true)
                        .setFieldValueEditor((fieldName, fieldValue) -> fieldValue == null ? null : fieldValue.toString())
        );

        //8.保存用户信息到Redis
        String tokenKey = LOGIN_USER_KEY + token;
        stringRedisTemplate.opsForHash().putAll(tokenKey, userMap);

        //9.设置token有效期
        stringRedisTemplate.expire(tokenKey, LOGIN_USER_TTL, TimeUnit.MINUTES);

        //10.返回token
        return Result.ok(token);
    }

    private User createUserWithPhone(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
        save(user);
        return user;
    }

    @Override
    public Result sign() {
        //1.获取当前登录用户
        Long userId = UserHolder.getUser().getId();

        //2.获取当前日期
        LocalDateTime now = LocalDateTime.now();

        //3.拼接key
        String key = SIGN_USER_KEY + userId + ":" + now.format(DateTimeFormatter.ofPattern("yyyyMM"));

        //4.获取今天是本月的第几天
        int dayOfMonth = now.getDayOfMonth();

        //5.写入 Redis Bitmap
        stringRedisTemplate.opsForValue().setBit(key, dayOfMonth - 1, true);

        //6.返回结果
        return Result.ok();
    }

    @Override
    public Result signCount() {
        //1.获取当前登录用户
        Long userId = UserHolder.getUser().getId();

        //2.获取当前日期
        LocalDateTime now = LocalDateTime.now();

        //3.拼接key
        String key = SIGN_USER_KEY + userId + ":" + now.format(DateTimeFormatter.ofPattern("yyyyMM"));

        //4.获取今天是本月的第几天
        int dayOfMonth = now.getDayOfMonth();

        //5.获取本月截至今天的所有见到记录，返回的是一个十进制数字
        List<Long> result = stringRedisTemplate.opsForValue().bitField(
                key,
                BitFieldSubCommands.create()
                        .get(BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth)).valueAt(0)
        );

        //6.判空
        if (result == null || result.isEmpty()) {
            return Result.ok(0);
        }
        Long num = result.get(0);
        if (num == null || num == 0) {
            return Result.ok(0);
        }

        //7.循环遍历，统计连续签到的天数
        int count = 0;
        while(true) {
            //判断最低位是否为0
            if((num % 1) == 0) {
                break;
            } else {
                count++;
            }
            //右移一位，继续下一个bit
            num >>>= 1;
        }

        //8.返回结果
        return Result.ok(count);
    }
}
