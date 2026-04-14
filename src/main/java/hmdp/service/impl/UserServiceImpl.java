package hmdp.service.impl;

import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import hmdp.dto.LoginFormDTO;
import hmdp.dto.Result;
import hmdp.entity.User;
import hmdp.mapper.UserMapper;
import hmdp.service.IUserService;
import hmdp.utils.RegexUtils;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

import static hmdp.utils.RedisConstants.LOGIN_CODE_KEY;
import static hmdp.utils.RedisConstants.LOGIN_CODE_TTL;

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
        return null;
    }
}
