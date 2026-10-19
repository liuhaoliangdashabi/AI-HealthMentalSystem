package org.example.aispringboot.service;

import ch.qos.logback.core.util.TimeUtil;
import cn.hutool.core.date.StopWatch;
import cn.hutool.crypto.digest.DigestUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.example.aispringboot.DTO.command.UserLoginCommandDTO;
import org.example.aispringboot.DTO.command.UserProfileUpdateCommandDTO;
import org.example.aispringboot.DTO.command.UserRegisterCommandDTO;
import org.example.aispringboot.DTO.response.UserLoginResponseDTO;
import org.example.aispringboot.entity.User;
import org.example.aispringboot.enumClass.UserType;
import org.example.aispringboot.exception.BusinessException;
import org.example.aispringboot.mapper.UserMapper;
import org.example.aispringboot.service.convert.UserConvert;
import org.example.aispringboot.util.JwtTokenUtil;
import org.example.aispringboot.util.OSSUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.TimeUnit;


@Slf4j
@Service
public class UserService {
    @Resource
    private UserMapper userMapper;

    private final BCryptPasswordEncoder passwordEncoder;
    private final JwtTokenUtil jwtTokenUtil;
    private static final String LOGIN_TRY_USERNAME_KEY="login:try:username:";
    private static final String LOGIN_USERNAME_BAN="login:ban:username:";
    private static final String LOGIN_TRY_IP_KEY="login:try:ip:";
    private static final String LOGIN_IP_BAN="login:ban:ip:";

    private static final int LOGIN_REQUEST_USERNAME_LIMIT_TIME = 10;
    private static final int LOGIN_REQUEST_IP_LIMIT_TIME = 30;
    @Autowired
    private StringRedisTemplate redis;
    @Autowired
    private OSSUtil ossUtil;

    public UserService(BCryptPasswordEncoder passwordEncoder,
                       JwtTokenUtil jwtTokenUtil,
                       StringRedisTemplate redis){
        this.passwordEncoder=passwordEncoder;
        this.jwtTokenUtil=jwtTokenUtil;
        this.redis=redis;
    }

    public UserLoginResponseDTO login(UserLoginCommandDTO commandDTO,
                                      HttpServletRequest request){
        checkIpLimit(request);
        Long times=null;
        String loginBan=null;
        try {
            long now=System.currentTimeMillis();
            loginBan=redis.opsForValue().get(LOGIN_USERNAME_BAN+commandDTO.getUsername());
            redis.opsForZSet().add(LOGIN_TRY_USERNAME_KEY+commandDTO.getUsername(),now+"-"+ UUID.randomUUID(),now);
            redis.opsForZSet().removeRangeByScore(LOGIN_TRY_USERNAME_KEY+commandDTO.getUsername(),0,now-60000);
            times=redis.opsForZSet().zCard(LOGIN_TRY_USERNAME_KEY+commandDTO.getUsername());
            redis.expire(LOGIN_TRY_USERNAME_KEY+commandDTO.getUsername(), Duration.ofMinutes(1));
        } catch (Exception e) {
            log.warn("登录请求做滑动窗口限流失败,快去维修",e);
        }
        if(times!=null && times>LOGIN_REQUEST_USERNAME_LIMIT_TIME || loginBan!=null && loginBan.equals("1")){
            loginBanUsername(commandDTO.getUsername());
            throw new BusinessException("当前用户请求次数过多，已被拉黑，请联系管理员");
        }

        //检验结束，业务开始
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
        Boolean passwordMatch=passwordEncoder.matches(inputPassword,user.getPassword());
        if(!passwordMatch.equals(Boolean.TRUE)){
            log.warn("登陆失败，密码错误，userId={}",user.getId());
            throw new BusinessException("密码错误");
        }
            if(!user.isActive()){
            log.warn("登陆失败，用户被禁用，userId={}",user.getId());
            throw new BusinessException("用户已被禁用，请联系管理员");
        }
        log.debug("判空、密码验证、用户状态均判断成功，准备生成token");
        String token= jwtTokenUtil.generateToken(user.getId(),user.getUsername(),user.getUserType());
        UserLoginResponseDTO.UserDetailResponseDTO userInfo= toDetailResponse(user);
        log.debug("走到这里，用户信息和装填已经获取，准备装填并返回");
        try{
            redis.delete(LOGIN_TRY_USERNAME_KEY+commandDTO.getUsername());
        }catch(Exception e){
            log.warn("用户重复登陆次数删除失败！",e);
        }
        return UserConvert.buildLoginResponse(token,userInfo);
    }

    public UserLoginResponseDTO.UserDetailResponseDTO register(UserRegisterCommandDTO commandDTO,
                                                               HttpServletRequest request){
        checkIpLimit(request);
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
        return toDetailResponse(user);
    }

    /**
     * 按 ID 查用户 —— 全站最高频的一次查询。
     *
     * <p>谁在调它：{@code JwtAuthticationFilter:78}，也就是<b>每一个带 token 的请求</b>。
     * 也就是说只要用户在页面上点一下，后端就要 select 一次 user 表。而 user 表是典型的
     * 「读极多、写极少」——一个月可能就改一次昵称。这种数据不进缓存纯属浪费。
     *
     * <p><b>为什么缓存 DTO 而不是 entity：</b>
     * entity 里有 {@code password}（bcrypt 哈希），进了 Redis 就等于把密码哈希抄了一份到缓存里。
     * {@code UserDetailResponseDTO} 没有这个字段，缓存的拷贝天然是脱敏的。
     *
     * <p><b>为什么可以缓存"已转成完整 URL 的 avatar"：</b>
     * 这里返回的 avatar 是 {@code OSSUtil.getUrl(objectKey)} 的结果。缓存住它意味着
     * OSS 域名一变更，缓存里的老域名要等 TTL（30 分钟）过期才纠正。这个代价可以接受，
     * 因为域名变更是一次性运维动作，不是业务行为。
     *
     * <p><b>⚠️ 所有写 user 表的方法都必须 {@code @CacheEvict}，否则用户会看到 30 分钟前的自己。</b>
     * 见 {@code updateProfile} / {@code updateAvatar}。
     */
    @Cacheable(cacheNames = "user", key = "#userId")
    public UserLoginResponseDTO.UserDetailResponseDTO getUserById(Long userId){
        User user=userMapper.selectById(userId);
        if(user==null){
            log.warn("查询用户不存在，userId={}",userId);
            throw new BusinessException("用户不存在");
        }
        return toDetailResponse(user);
    }

    /**
     * 修改个人资料。
     *
     * 用 LambdaUpdateWrapper 显式 set 而不是 updateById —— updateById 会跳过 null 字段，
     * 那样用户想把手机号清空就永远清不掉。
     *
     * @return 更新后的最新用户信息（前端直接拿去刷 localStorage，省一次 /user/current）
     */
    @CacheEvict(cacheNames = "user", key = "#userId")
    public UserLoginResponseDTO.UserDetailResponseDTO updateProfile(Long userId,
                                                                   UserProfileUpdateCommandDTO dto){
        User user=userMapper.selectById(userId);
        if(user==null){
            log.warn("修改资料时用户不存在，userId={}",userId);
            throw new BusinessException("用户不存在");
        }

        String newEmail=dto.getEmail().trim();
        //邮箱是登录凭据之一（login 里 eq(username) or eq(email)），撞了就是两个人抢同一个账号
        if(!newEmail.equalsIgnoreCase(user.getEmail())){
            LambdaQueryWrapper<User> qw=new LambdaQueryWrapper<User>()
                    .eq(User::getEmail,newEmail)
                    .ne(User::getId,userId);
            if(userMapper.selectCount(qw)>0){
                log.warn("修改资料失败，邮箱已被占用，userId={}，email={}",userId,newEmail);
                throw new BusinessException("该邮箱已被其他账号使用");
            }
        }

        String newNickname=dto.getNickname()==null?null:dto.getNickname().trim();
        String newPhone=dto.getPhone()==null?null:dto.getPhone().trim();

        //部分更新语义：字段为 null 表示"这次不改"，不要 set。
        //手机号额外支持"传空串 = 清空"，否则用户永远删不掉已经填过的号码。
        LambdaUpdateWrapper<User> uw=new LambdaUpdateWrapper<User>()
                .eq(User::getId,userId)
                .set(User::getEmail,newEmail)
                .set(User::getUpdatedAt,LocalDateTime.now());
        if(newNickname!=null){
            uw.set(User::getNickname,newNickname);
        }
        if(newPhone!=null){
            uw.set(User::getPhone,newPhone.isEmpty()?null:newPhone);
        }
        if(dto.getGender()!=null){
            uw.set(User::getGender,dto.getGender());
        }
        if(dto.getBirthday()!=null){
            uw.set(User::getBirthday,dto.getBirthday());
        }
        userMapper.update(null,uw);

        log.debug("用户资料已更新，userId={}",userId);
        return toDetailResponse(userMapper.selectById(userId));
    }

    /**
     * 换头像。入参是上传接口返回的 objectKey（相对路径），不是完整 URL。
     * 存 objectKey 而不是 URL 的原因：OSS 域名/协议一变，库里几千条 URL 全废，objectKey 不会。
     *
     * @return 换完之后可访问的完整 URL，前端直接拿去渲染，省一次 /user/current
     */
    @CacheEvict(cacheNames = "user", key = "#userId")
    public String updateAvatar(Long userId, String avatar){
        if(avatar==null || avatar.isBlank()){
            throw new BusinessException("头像不能为空");
        }
        User user=userMapper.selectById(userId);
        if(user==null){
            log.warn("换头像时用户不存在，userId={}",userId);
            throw new BusinessException("用户不存在");
        }
        String newAvatar=avatar.trim();
        String oldAvatar=user.getAvatar();

        User update=new User();
        update.setId(userId);
        update.setAvatar(newAvatar);
        update.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(update);

        //旧头像顺手删掉，别在 OSS 里攒垃圾。
        //delete 内部已经吞异常了，删失败不影响换头像本身。
        if(oldAvatar!=null && !oldAvatar.isBlank() && !oldAvatar.equals(newAvatar)){
            ossUtil.delete(oldAvatar);
        }
        return ossUtil.getUrl(newAvatar);
    }

    /**
     * 出口统一收口：库里存 objectKey，返回给前端前转成完整 URL。
     * 所有返回 UserDetailResponseDTO 的地方都必须走这里，漏一个前端就有一处破图。
     */
    private UserLoginResponseDTO.UserDetailResponseDTO toDetailResponse(User user){
        UserLoginResponseDTO.UserDetailResponseDTO dto=UserConvert.entityToDetailResponse(user);
        dto.setAvatar(ossUtil.getUrl(dto.getAvatar()));
        return dto;
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

    public void loginBanUsername(String username){
        try{
            redis.opsForValue().set(LOGIN_USERNAME_BAN+username,"1",Duration.ofMinutes(3));
        }catch(Exception e){
            log.warn("登录超限,用户名username={}加入黑名单失败，赶快修复",username,e);
        }
    }

    private void checkIpLimit(HttpServletRequest request){
        String ip=getClientIp(request);
        Long times=null;
        String loginBan=null;
        try {
            long now=System.currentTimeMillis();
            loginBan=redis.opsForValue().get(LOGIN_IP_BAN+ip);
            redis.opsForZSet().add(LOGIN_TRY_IP_KEY+ip,now+"-"+ UUID.randomUUID(),now);
            redis.opsForZSet().removeRangeByScore(LOGIN_TRY_IP_KEY+ip,0,now-60000);
            times=redis.opsForZSet().zCard(LOGIN_TRY_IP_KEY+ip);
            redis.expire(LOGIN_TRY_IP_KEY+ip, Duration.ofMinutes(1));
        } catch (Exception e) {
            log.warn("登录请求做滑动窗口限流失败,快去维修",e);
        }
        if(times!=null && times>LOGIN_REQUEST_IP_LIMIT_TIME || loginBan!=null && loginBan.equals("1")){
            loginBanIP(ip);
            throw new BusinessException("当前用户请求次数过多，已被拉黑，请联系管理员");
        }
    }

    private String getClientIp(HttpServletRequest request){
        String ip=request.getRemoteAddr();
        if(ip==null)return "unknown";
        if("0:0:0:0:0:0:0:1".equals(ip)||"::1".equals(ip))return "127.0.0.1";
        if(ip.startsWith("::ffff:"))return ip.substring(7);
        return ip;
    }

    private void loginBanIP(String ip){
        try{
            redis.opsForValue().set(LOGIN_IP_BAN+ip,"1",Duration.ofMinutes(3));
        }catch(Exception e){
            log.warn("登录超限，用户IP={}加入黑名单失败",ip,e);
        }
    }
}
