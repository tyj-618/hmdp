package hmdp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import hmdp.dto.Result;
import hmdp.entity.Blog;

public interface IBlogService extends IService<Blog> {
    //current表示页码
    Result queryHotBlog(Integer current);

    Result queryBlogById(Long id);

    Result likeBlog(Long id);

    Result queryBlogLikes(Long id);
}
