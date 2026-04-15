package hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import hmdp.dto.Result;
import hmdp.dto.UserDTO;
import hmdp.entity.Follow;
import hmdp.mapper.FollowMapper;
import hmdp.service.IFollowService;
import hmdp.service.IUserService;
import hmdp.utils.UserHolder;
import jakarta.annotation.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static hmdp.utils.RedisConstants.FOLLOW_KEY;

@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private IUserService userService;

    @Override
    public Result follow(Long followUserId, Boolean isFollow) {
        //1.获取当前登录用户
        Long userId = UserHolder.getUser().getId();
        String key = FOLLOW_KEY + userId;

        //2.判断是关注还是取关
        if (Boolean.TRUE.equals(isFollow)) {
            //关注：保存到数据库
            Follow follow = new Follow();
            follow.setUserId(userId);
            follow.setFollowUserId(followUserId);
            boolean success = save(follow);
            if (success) {
                stringRedisTemplate.opsForSet().add(key, followUserId.toString());
            }
        } else {
            //取关：删除数据库记录
            boolean success = remove(new QueryWrapper<Follow>()
                    .eq("user_id", userId)
                    .eq("follow_user_id", followUserId));
            if (success) {
                stringRedisTemplate.opsForSet().remove(key, followUserId.toString());
            }
        }

        return Result.ok();
    }

    @Override
    public Result isFollow(Long followUserId) {
        Long userId = UserHolder.getUser().getId();

        Long count = query()
                .eq("user_id", userId)
                .eq("follow_user_id", followUserId)
                .count();

        return Result.ok(count > 0);
    }

    @Override
    public Result followCommons(Long id) {
        //1.获取当前用户
        Long userId = UserHolder.getUser().getId();

        //2.求交集
        String key1 = FOLLOW_KEY + userId;
        String key2 = FOLLOW_KEY + id;
        Set<String> intersect = stringRedisTemplate.opsForSet().intersect(key1, key2);

        if (intersect == null || intersect.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }

        //3.解析用户id
        List<Long> ids = intersect.stream().map(Long::valueOf).collect(Collectors.toList());

        //4.查询用户
        List<UserDTO> users = userService.listByIds(ids).stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());

        return Result.ok(users);
    }
}
