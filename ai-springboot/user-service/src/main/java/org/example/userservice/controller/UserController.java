package org.example.userservice.controller;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.example.common.Result;
import org.example.common.annotation.RequireIdentity;
import org.example.common.util.IdentityUtil;
import org.example.userservice.DTO.command.UserLoginCommandDTO;
import org.example.userservice.DTO.command.UserProfileUpdateCommandDTO;
import org.example.userservice.DTO.command.UserRegisterCommandDTO;
import org.example.userservice.DTO.response.UserLoginResponseDTO;
import org.example.userservice.service.SignService;
import org.example.userservice.service.UserService;
import org.example.userservice.util.JwtTokenUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 用户域对外接口。
 *
 * 和 web 那版的三处差别：
 * 1. 没有 @AuthenticationPrincipal —— 本服务不引 starter-security，
 *    身份一律读网关注入的 X-User-* 头（IdentityInterceptor 已保证缺失时 401）。
 * 2. /user/activity 不在本服务 —— 它要同时读日记、文章、会话，是跨域聚合，
 *    放这里会让 user-service 反向依赖别的服务。暂时留在 web。
 * 3. 登录、注册不加 @RequireIdentity（网关白名单放行），其余全部要求身份。
 */
@Slf4j
@RestController
@RequestMapping("/api")
public class UserController {

    @Autowired
    private UserService userService;
    @Resource
    private JwtTokenUtil jwtTokenUtil;
    @Autowired
    private SignService signService;

    @PostMapping("/user/login")
    public Result<UserLoginResponseDTO> login(@Valid @RequestBody UserLoginCommandDTO commandDTO,
                                              HttpServletRequest request) {
        log.debug("登录请求的controller层，username = {}", commandDTO.getUsername());
        UserLoginResponseDTO responseDTO = userService.login(commandDTO, request);
        return Result.success(responseDTO);
    }

    @PostMapping("/user/add")
    public Result<UserLoginResponseDTO.UserDetailResponseDTO> register(@Valid @RequestBody UserRegisterCommandDTO commandDTO,
                                                                       HttpServletRequest request) {
        log.debug("注册请求的controller层：{}", commandDTO);
        return Result.success(userService.register(commandDTO, request));
    }

    @GetMapping("/user/current")
    @RequireIdentity
    public Result<UserLoginResponseDTO.UserDetailResponseDTO> getCurrentUser(
            @RequestHeader(name = IdentityUtil.HEADER_USER_ID) Long userId) {
        log.debug("Id={}查询当前用户信息", userId);
        //走 @Cacheable，30 分钟内的重复请求不落库
        return Result.success(userService.getUserById(userId));
    }

    /**
     * 登出。token 原文从请求头拿 —— 网关不清洗这个头，所以到这里还是原值，
     * 不需要网关额外注入一份，也不用 SecurityContext。
     */
    @PostMapping("/user/logout")
    @RequireIdentity
    public Result<Void> logout() {
        String token = jwtTokenUtil.getCurrentToken();
        log.debug("用户退出登录，token 是否为空={}", token == null);
        userService.logout(token);
        return Result.success();
    }

    @PostMapping("/user/sign")
    @RequireIdentity
    public Result<Boolean> sign(@RequestHeader(name = IdentityUtil.HEADER_USER_ID) Long userId) {
        log.debug("Id={}签到", userId);
        return Result.success(signService.sign(userId));
    }

    @GetMapping("/user/sign/status")
    @RequireIdentity
    public Result<Map<String, Object>> getSignStatus(@RequestHeader(name = IdentityUtil.HEADER_USER_ID) Long userId) {
        log.debug("Id={}查询近期签到状态", userId);
        return Result.success(signService.getSignStatus(userId));
    }

    //换头像。body 里传 objectKey（上传接口的 filePath），不是完整 URL
    @PutMapping("/user/avatar")
    @RequireIdentity
    public Result<Map<String, String>> updateAvatar(@RequestBody Map<String, String> body,
                                                    @RequestHeader(name = IdentityUtil.HEADER_USER_ID) Long userId) {
        String avatar = body == null ? null : body.get("avatar");
        log.debug("Id={}更换头像，avatar={}", userId, avatar);
        return Result.success(Map.of("avatar", userService.updateAvatar(userId, avatar)));
    }

    //修改个人资料（昵称/邮箱/手机/性别/生日）。用户名和密码不在这个接口里
    @PutMapping("/user/profile")
    @RequireIdentity
    public Result<UserLoginResponseDTO.UserDetailResponseDTO> updateProfile(
            @Valid @RequestBody UserProfileUpdateCommandDTO commandDTO,
            @RequestHeader(name = IdentityUtil.HEADER_USER_ID) Long userId) {
        log.debug("Id={}修改个人资料", userId);
        return Result.success(userService.updateProfile(userId, commandDTO));
    }
}
