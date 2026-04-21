package hmdp.service.impl;

import hmdp.dto.Result;
import hmdp.service.IUVService;
import hmdp.utils.UserHolder;
import jakarta.annotation.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import static hmdp.utils.RedisConstants.UV_KEY;

@Service
public class UVServiceImpl implements IUVService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy:MM:dd");

    @Override
    public Result recordUV() {
        Long userId = UserHolder.getUser().getId();
        String key = UV_KEY + LocalDate.now().format(FORMATTER);

        stringRedisTemplate.opsForHyperLogLog().add(key, userId.toString());

        return Result.ok();
    }

    /**
     *统计口径：按用户登录ID
     * 统计周期：按天
     * 统计结构：HyperLogLog近似去重
     */
    @Override
    public Result countUV(String date) {
        String key;

        if (date == null || date.isBlank()) {
            key = UV_KEY + LocalDate.now().format(FORMATTER);
        } else  {
            key = UV_KEY + date;
        }

        Long count = stringRedisTemplate.opsForHyperLogLog().size(key);
        return Result.ok(count == null ? 0 : count);
    }
}
