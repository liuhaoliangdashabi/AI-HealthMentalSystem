package org.example.aispringboot.service;

import ch.qos.logback.core.util.TimeUtil;
import cn.hutool.crypto.digest.DigestUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.example.aispringboot.DTO.command.UserLoginCommandDTO;
import org.example.aispringboot.DTO.command.UserRegisterCommandDTO;
import org.example.aispringboot.DTO.response.UserLoginResponseDTO;
import org.example.aispringboot.entity.User;
import org.example.aispringboot.enumClass.UserType;
import org.example.aispringboot.exception.BusinessException;
import org.example.aispringboot.mapper.UserMapper;
import org.example.aispringboot.service.convert.UserConvert;
import org.example.aispringboot.util.JwtTokenUtil;
import org.springframework.cache.annotation.CachePut;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.concurrent.TimeUnit;


@Slf4j
@Service
public class UserService {
    @Resource
    private UserMapper userMapper;

    private final BCryptPasswordEncoder passwordEncoder;
    private final JwtTokenUtil jwtTokenUtil;
    private StringRedisTemplate redis;

    public UserService(BCryptPasswordEncoder passwordEncoder,
                       JwtTokenUtil jwtTokenUtil,
                       StringRedisTemplate redis){
        this.passwordEncoder=passwordEncoder;
        this.jwtTokenUtil=jwtTokenUtil;
        this.redis=redis;
    }

    public UserLoginResponseDTO login(UserLoginCommandDTO commandDTO){
        log.info("登录请求的service层：{}",commandDTO);
        LambdaQueryWrapper<User> queryWrapper=new LambdaQueryWrapper<>();
        queryWrapper.eq(User::getUsername,commandDTO.getUsername())
                .or()
                .eq(User::getEmail,commandDTO.getUsername());
        User user=userMapper.selectOne(queryWrapper);

        if(user==null){
            log.warn("登陆失败，用户不存在，username={}",commandDTO.getUsername());
            throw new BusinessException("查找的用户不存在");
        }

        String inputPassword=commandDTO.getPassword().trim();
        if(!passwordEncoder.matches(inputPassword,user.getPassword())){
            log.warn("登陆失败，密码错误，userId={}",user.getId());
            throw new BusinessException("密码错误");
        }
        if(!user.isActive()){
            log.warn("登陆失败，用户被禁用，userId={}",user.getId());
            throw new BusinessException("用户已被禁用，请联系管理员");
        }
        log.debug("判空、密码验证、用户状态均判断成功，准备生成token");
        String token= jwtTokenUtil.generateToken(user.getId(),user.getUsername(),user.getUserType());
        UserLoginResponseDTO.UserDetailResponseDTO userInfo= UserConvert.entityToDetailResponse(user);
        log.debug("走到这里，用户信息和装填已经获取，准备装填并返回");
        return UserConvert.buildLoginResponse(token,userInfo);
    }

    public UserLoginResponseDTO.UserDetailResponseDTO register(UserRegisterCommandDTO commandDTO){
        if(!commandDTO.getPassword().equals(commandDTO.getConfirmPassword())){
            throw new BusinessException("两次输入密码不一致");
        }
        //用户名不能重复，检查用户名是否存在
        LambdaQueryWrapper<User> userNameQuery=new LambdaQueryWrapper<>();
        userNameQuery.eq(User::getUsername,commandDTO.getUsername());
        if(userMapper.selectCount(userNameQuery)>0){
            log.warn("用户名已存在，username={}",commandDTO.getUsername());
            throw new BusinessException("用户名已存在");
        }
        //检查邮箱是否存在
        LambdaQueryWrapper<User> emailQuery=new LambdaQueryWrapper<>();
        emailQuery.eq(User::getEmail,commandDTO.getEmail());
        if(userMapper.selectCount(emailQuery)>0){
            log.warn("用户名/邮箱已存在，username={},email={}",commandDTO.getUsername(),commandDTO.getEmail());
            throw new BusinessException("用户名/邮箱已存在");
        }
        //用户类型验证——传入类型要有意义
        if(!UserType.isValidCode(commandDTO.getUserType())){
            log.warn("用户类型无效，userType={}",commandDTO.getUserType());
            throw new BusinessException("无效的用户类型");
        }
        commandDTO.setUserType(UserType.USER.getCode());
        String encodingPassword=passwordEncoder.encode(commandDTO.getPassword().trim());
        User user= UserConvert.registerCommandToEntity(commandDTO,encodingPassword);
        userMapper.insert(user);
        return UserConvert.entityToDetailResponse(user);
    }

    public UserLoginResponseDTO.UserDetailResponseDTO getUserById(Long userId){
        User user=userMapper.selectById(userId);
        if(user==null){
            log.warn("查询用户不存在，userId={}",userId);
            throw new BusinessException("用户不存在");
        }
        return UserConvert.entityToDetailResponse(user);
    }


    public void logout(String token) {
            Long exp= jwtTokenUtil.verifyToken(token).getExpiresAt().getTime();
            Long remainTime=exp-System.currentTimeMillis();
            if(remainTime<=0)return;
        try {
            redis.opsForValue().set("blacklist:token:"+ DigestUtil.md5Hex(token),"1",remainTime, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            log.error("登出写黑名单失败，Redis出问题了，赶紧修复！",e);
            throw new BusinessException("登出失败，请重试");
        }
    }
}
