package org.example.aispringboot.service;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import org.example.aispringboot.DTO.command.ConsultationSectionCreateDTO;
import org.example.aispringboot.entity.ConsultationSession;
import org.example.aispringboot.entity.User;
import org.example.aispringboot.exception.BusinessException;
import org.example.aispringboot.mapper.ConsultationSessionMapper;
import org.example.aispringboot.mapper.UserMapper;
import org.example.aispringboot.util.RedisKeys;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Date;
@Slf4j
@Service
public class ConsultationSessionsService {
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private ConsultationSessionMapper consultationSessionMapper;
    @Autowired
    private StringRedisTemplate redis;

    public ConsultationSession createSession(Long userId, ConsultationSectionCreateDTO createDTO){
        //先验证用户是否存在，数据插入才有意义
        User user=userMapper.selectById(userId);
        if(user!=null){
            ConsultationSession session=ConsultationSession.builder()
                    .userId(userId)
                    .sessionTitle(createDTO.getSessionTitle())
                    .startedAt(LocalDateTime.now())
                    .build();
            if(StrUtil.isBlank(createDTO.getSessionTitle())){
                session.setSessionTitle(String.format("宁渡AI助手 - " + DateUtil.format(LocalDateTime.now(),"MM-dd-HH:mm") ));
            }

            consultationSessionMapper.insert(session);
            try{
                String key= RedisKeys.uv(session.getStartedAt().toLocalDate());
                redis.opsForHyperLogLog().add(key,String.valueOf(userId));
                if(!Boolean.TRUE.equals(redis.expire(key,RedisKeys.UV_TTL))){
                    log.warn("UV key设过期时间失败，key={}",key);
                }
            }catch(Exception e){
                log.warn("userId={}的UV记录失败，快去修复",userId,e);
            }
            return session;
        }
        log.warn("查找会话失败——用户不存在：userId={}",userId);
        throw new BusinessException("用户不存在");
    }
}
