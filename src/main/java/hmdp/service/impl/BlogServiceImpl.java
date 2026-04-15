package hmdp.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import hmdp.dto.Result;
import hmdp.entity.Blog;
import hmdp.entity.User;
import hmdp.mapper.BlogMapper;
import hmdp.service.IBlogService;
import hmdp.service.IUserService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.List;

import static hmdp.utils.SystemConstants.MAX_PAGE_SIZE;

@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {

    @Resource
    private IUserService userService;

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

        //3.返回
        return Result.ok(blog);
    }

    @Override
    public Result likeBlog(Long id) {
        return null;
    }

    @Override
    public Result queryBlogLikes(Long id) {
        return null;
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
}
