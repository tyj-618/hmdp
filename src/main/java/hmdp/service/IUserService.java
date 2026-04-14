package hmdp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import hmdp.dto.LoginFormDTO;
import hmdp.dto.Result;
import hmdp.entity.User;
import jakarta.servlet.http.HttpSession;

public interface IUserService extends IService<User> {

    Result sendCode(String phone, HttpSession session);

    Result login(LoginFormDTO loginForm, HttpSession session);
}
