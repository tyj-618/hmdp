package hmdp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import hmdp.dto.Result;
import hmdp.entity.Follow;

public interface IFollowService extends IService<Follow> {

    Result follow(Long followUserId, Boolean isFollow);

    Result isFollow(Long followUserId);

    Result followCommons(Long id);
}
