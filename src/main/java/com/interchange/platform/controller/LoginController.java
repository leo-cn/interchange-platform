package com.interchange.platform.controller;

import com.interchange.platform.common.BizException;
import com.interchange.platform.common.LoginUser;
import com.interchange.platform.common.LoginUserHolder;
import com.interchange.platform.common.R;
import com.interchange.platform.service.AuthService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 登录与登出
 */
@Controller
public class LoginController {

    private final AuthService authService;

    public LoginController(AuthService authService) {
        this.authService = authService;
    }

    @GetMapping("/login")
    public String loginPage(HttpSession session, Model model,
                           @RequestParam(value = "redirect", required = false) String redirect) {
        if (LoginUserHolder.get(session) != null) {
            return "redirect:/";
        }
        model.addAttribute("redirect", redirect);
        return "login";
    }

    @PostMapping("/doLogin")
    @ResponseBody
    public R<Map<String, Object>> doLogin(@RequestParam(value = "username", required = false) String username,
                                          @RequestParam(value = "password", required = false) String password,
                                          HttpSession session) {
        // 兜底：前端校验挡掉了绝大多数情况，但直接用 curl/工具调用时仍可能传空，
        // 这里给出人话提示，而不是抛 MissingServletRequestParameterException 导致 500。
        if (username == null || username.trim().isEmpty()) {
            throw new BizException(400, "请输入用户名");
        }
        if (password == null || password.isEmpty()) {
            throw new BizException(400, "请输入密码");
        }
        LoginUser user = authService.login(username.trim(), password);
        LoginUserHolder.save(session, user);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("username", user.getUsername());
        data.put("displayName", user.getDisplayName());
        data.put("role", user.getRole());
        return R.ok("登录成功", data);
    }

    @GetMapping("/logout")
    public String logout(HttpSession session) {
        LoginUserHolder.clear(session);
        session.invalidate();
        return "redirect:/login";
    }

    /** 重置本人接口令牌（第三方调用接收接口时使用） */
    @PostMapping("/api/profile/token")
    @ResponseBody
    public R<String> resetApiToken() {
        LoginUser current = LoginUserHolder.current();
        if (current == null) {
            throw new BizException(401, "登录已失效，请重新登录");
        }
        return R.ok("令牌已重置", authService.resetApiToken(current.getId()));
    }

    /** 修改本人口令 */
    @PostMapping("/api/profile/password")
    @ResponseBody
    public R<String> changePassword(@RequestParam(value = "oldPassword", required = false) String oldPassword,
                                    @RequestParam(value = "newPassword", required = false) String newPassword) {
        LoginUser current = LoginUserHolder.current();
        if (current == null) {
            throw new BizException(401, "登录已失效，请重新登录");
        }
        if (oldPassword == null || oldPassword.isEmpty()) {
            throw new BizException(400, "请输入当前密码");
        }
        if (newPassword == null || newPassword.length() < 6) {
            throw new BizException(400, "新密码长度不能少于 6 位");
        }
        authService.changePassword(current.getId(), oldPassword, newPassword);
        return R.ok("密码修改成功，请重新登录", null);
    }
}
