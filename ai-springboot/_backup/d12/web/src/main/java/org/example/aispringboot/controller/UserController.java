package org.example.aispringboot.controller;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.example.aispringboot.DTO.command.UserLoginCommandDTO;
import org.example.aispringboot.DTO.command.UserProfileUpdateCommandDTO;
import org.example.aispringboot.DTO.command.UserRegisterCommandDTO;
import org.example.aispringboot.DTO.response.UserActivityItem;
import org.example.aispringboot.DTO.response.UserLoginResponseDTO;
import org.example.common.Result;
import org.example.aispringboot.service.DiaryService;
import org.example.aispringboot.service.SignService;
import org.example.aispringboot.service.UserService;
import org.example.aispringboot.util.JwtTokenUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api")
public class UserController {
    @Autowired
    private UserService userService;
    @Autowired
    private DiaryService diaryService;
    @Resource
    private JwtTokenUtil jwtTokenUtil;
    @Autowired
    private SignService signService;

    @PostMapping("/user/login")
    public Result<UserLoginResponseDTO> login(@Valid @RequestBody UserLoginCommandDTO commandDTO,
                                              HttpServletRequest request) {
        log.debug("登录请求的controller层，username = {}", commandDTO.getUsername());
        UserLoginResponseDTO responseDTO = userService.login(commandDTO,request);
        return Result.success(responseDTO);
    }

    @PostMapping("/user/add")
    public Result<UserLoginResponseDTO.UserDetailResponseDTO> register(@Valid @RequestBody UserRegisterCommandDTO commandDTO,
                                                                       HttpServletRequest request) {
        log.debug("注册请求的controller层：{}", commandDTO);
        UserLoginResponseDTO.UserDetailResponseDTO result = userService.register(commandDTO,request);
        return Result.success(result);
    }

    @GetMapping("/user/current")
    public Result<UserLoginResponseDTO.UserDetailResponseDTO> getCurrentUser(
            @AuthenticationPrincipal UserLoginResponseDTO.UserDetailResponseDTO user
    ){
        log.debug("通过token获取当前用户信息——从安全上下文中");
        return Result.success(user);
    }
    @PostMapping("/user/logout")
    public Result<Void> logout(){
        log.debug("用户退出登录");
        String token=jwtTokenUtil.getCurrentToken();
        userService.logout(token);
        return Result.success();
    }

    // 5 个 /emotion-diary/** 接口已在 D7 迁到 diary-service(8030)，此处不再保留，避免走 8080 写成双份实现

    @PostMapping("/user/sign")
    public Result<Boolean> sign(@AuthenticationPrincipal UserLoginResponseDTO.UserDetailResponseDTO user){
        log.debug("Id={}签到",user.getId());
        return Result.success(signService.sign(user.getId()));
    }

    @GetMapping("/user/sign/status")
    public Result<Map<String,Object>> getSignStatus(@AuthenticationPrincipal UserLoginResponseDTO.UserDetailResponseDTO user){
        log.debug("Id={}查询近期签到状态",user.getId());
        return Result.success(signService.getSignStatus(user.getId()));
    }

    //个人主页贡献日历：13 周 = 91 天，跟前端网格列数对齐
    private static final int PROFILE_ACTIVITY_DAYS=91;

    @GetMapping("/user/activity")
    public Result<List<UserActivityItem>> getMyActivity(
            @AuthenticationPrincipal UserLoginResponseDTO.UserDetailResponseDTO user){
        log.debug("Id={}查询个人主页活跃记录",user.getId());
        return Result.success(diaryService.listRecentActivity(user.getId(),PROFILE_ACTIVITY_DAYS));
    }

    //换头像。body 里传 objectKey（上传接口的 filePath），不是完整 URL
    @PutMapping("/user/avatar")
    public Result<Map<String,String>> updateAvatar(@RequestBody Map<String,String> body,
                                                   @AuthenticationPrincipal UserLoginResponseDTO.UserDetailResponseDTO user){
        String avatar=body==null?null:body.get("avatar");
        log.debug("Id={}更换头像，avatar={}",user.getId(),avatar);
        return Result.success(Map.of("avatar",userService.updateAvatar(user.getId(),avatar)));
    }

    //修改个人资料（昵称/邮箱/手机/性别/生日）。用户名和密码不在这个接口里
    @PutMapping("/user/profile")
    public Result<UserLoginResponseDTO.UserDetailResponseDTO> updateProfile(
            @Valid @RequestBody UserProfileUpdateCommandDTO commandDTO,
            @AuthenticationPrincipal UserLoginResponseDTO.UserDetailResponseDTO user){
        log.debug("Id={}修改个人资料",user.getId());
        return Result.success(userService.updateProfile(user.getId(),commandDTO));
    }

}