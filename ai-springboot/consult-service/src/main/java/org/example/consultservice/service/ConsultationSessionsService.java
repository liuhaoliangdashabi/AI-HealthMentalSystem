package org.example.consultservice.service;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import org.example.common.BusinessException;
import org.example.consultservice.DTO.command.ConsultationSectionCreateDTO;
import org.example.consultservice.entity.ConsultationSession;
import org.example.consultservice.mapper.ConsultationSessionMapper;
import org.example.consultservice.util.RedisKeys;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Slf4j
@Service
public class ConsultationSessionsService {
    @Autowired
    private ConsultationSessionMapper consultationSessionMapper;
    @Autowired
    private StringRedisTemplate redis;

    public ConsultationSession createSession(Long userId, ConsultationSectionCreateDTO createDTO){
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
}
