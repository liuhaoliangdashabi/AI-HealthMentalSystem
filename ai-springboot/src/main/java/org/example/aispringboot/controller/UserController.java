package org.example.aispringboot.controller;

import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.example.aispringboot.DTO.command.DiaryCommandDTO;
import org.example.aispringboot.DTO.command.DiaryPageQueryDTO;
import org.example.aispringboot.DTO.command.UserLoginCommandDTO;
import org.example.aispringboot.DTO.command.UserRegisterCommandDTO;
import org.example.aispringboot.DTO.response.DiaryResponseDTO;
import org.example.aispringboot.DTO.response.UserLoginResponseDTO;
import org.example.aispringboot.common.Result;
import org.example.aispringboot.exception.BusinessException;
import org.example.aispringboot.service.DiaryService;
import org.example.aispringboot.service.SignService;
import org.example.aispringboot.service.UserService;
import org.example.aispringboot.util.JwtTokenUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

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

    @PostMapping("/emotion-diary")
    public Result<Void> addEmotionDiary(@Valid @RequestBody DiaryCommandDTO commandDTO,
                                        @AuthenticationPrincipal UserLoginResponseDTO.UserDetailResponseDTO user){
        log.debug("Id={}提交情绪日记",user.getId());
        diaryService.addEmotionDiary(commandDTO,user.getId());
        return Result.success();
    }

    @GetMapping("/emotion-diary/page")
    public Result<IPage<DiaryResponseDTO>> getMyDiaries(DiaryPageQueryDTO queryDTO,
                                                        @AuthenticationPrincipal UserLoginResponseDTO.UserDetailResponseDTO user){
        log.debug("Id={}要查询Id={}的日志，已改成自己的Id",user.getId(),queryDTO.getUserId());
        //管你这的那的，C端查自己的
        queryDTO.setUserId(user.getId());
        return Result.success(diaryService.getEmotionalPage(queryDTO,user.getId(),false));
    }

    @DeleteMapping("/emotion-diary/{id}")
    public Result<Void> deleteEmotionDiary(@PathVariable("id")Long diaryId,
                                           @AuthenticationPrincipal UserLoginResponseDTO.UserDetailResponseDTO user){
        log.debug("Id{}要删除diary={}的日记",user.getId(),diaryId);
        diaryService.deleteEmotionDiary(diaryId,user.getId(),false);
        return Result.success();
    }

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

}