package hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import hmdp.dto.Result;
import hmdp.dto.UserDTO;
import hmdp.entity.Blog;
import hmdp.entity.User;
import hmdp.mapper.BlogMapper;
import hmdp.service.IBlogService;
import hmdp.service.IUserService;
import hmdp.utils.UserHolder;
import jakarta.annotation.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static hmdp.utils.RedisConstants.BLOG_LIKED_KEY;
import static hmdp.utils.SystemConstants.MAX_PAGE_SIZE;

@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {

    @Resource
    private IUserService userService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryHotBlog(Integer current) {
        //1.按点赞倒序分页查询
        Page<Blog> page = query()
                .orderByDesc("liked")
                .page(new Page<>(current, MAX_PAGE_SIZE));

        //2.获取当前页数据
        List<Blog> records = page.getRecords();

        //3.补充作者信息
        for (Blog blog : records) {
            queryBlogUser(blog);
            isBlogLiked(blog);
        }

        //4.返回结果
        return Result.ok(records);
    }

    @Override
    public Result queryBlogById(Long id) {
        //1.查询博客
        Blog blog = getById(id);
        if (blog == null) {
            return Result.fail("笔记不存在！");
        }

        //2.补充作者信息
        queryBlogUser(blog);
        isBlogLiked(blog);

        //3.返回
        return Result.ok(blog);
    }

    @Override
    public Result likeBlog(Long id) {
        //1.获取当前登录用户
        Long userId = UserHolder.getUser().getId();

        //2.拼接key
        String key = BLOG_LIKED_KEY + id;

        //3.判断当前用户是否已经点赞
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());

        if (score == null) {
            //4.未点赞，可以点赞
            boolean isSuccess = update().setSql("liked = liked + 1").eq("id", id).update();
            if (isSuccess) {
                stringRedisTemplate.opsForZSet().add(key, userId.toString(), System.currentTimeMillis());
            }
        } else {
            //5.已点赞，取消点赞
            boolean isSuccess = update().setSql("liked = liked - 1").eq("id", id).update();
            if (isSuccess) {
                stringRedisTemplate.opsForZSet().remove(key, userId.toString());
            }
        }

        return Result.ok();
    }

    @Override
    public Result queryBlogLikes(Long id) {
        //1.拼接key
        String key = BLOG_LIKED_KEY + id;

        //2.查询前5个点赞用户
        Set<String> top5 = stringRedisTemplate.opsForZSet().range(key, 0, 4);
        if (top5 == null || top5.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }

        //3.解析出其中的用户id
        List<Long> ids = top5.stream().map(Long::valueOf).collect(Collectors.toList());

        //4.拼接id字符串，保证查询结果顺序与Redis中一致
        String idStr = ids.stream().map(String::valueOf).collect(Collectors.joining(","));

        //5.根据用户id查询用户
        List<UserDTO> userDTOS = userService.query()
                .in("id", ids)
                .last("ORDER BY FIELD(id," + idStr + ")")
                .list()
                .stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());

        //6.返回
        return Result.ok(userDTOS);
    }

    private void queryBlogUser(Blog blog) {
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        if (user == null) {
            return;
        }
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
    }

    private void isBlogLiked(Blog blog) {
        //1.获取当前登录用户
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return;
        }

        //2.获取用户id
        Long userId = user.getId();

        //3.拼接key
        String key = BLOG_LIKED_KEY + blog.getId();

        //4.判断是否点赞
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());

        //5.赋值
        blog.setIsLike(score != null);
    }
}
