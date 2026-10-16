package org.example.aispringboot.service;

import lombok.extern.slf4j.Slf4j;
import org.example.aispringboot.exception.BusinessException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.cert.LDAPCertStoreParameters;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class SignService {
    @Autowired
    private StringRedisTemplate redis;

    private static final String SIGN_KEY="sign:";
    private static final Duration SIGN_TTL= Duration.ofDays(40);
    private static final DateTimeFormatter MONTH_FMT= DateTimeFormatter.ofPattern("yyyy-MM");

    public boolean sign(Long userId){
        LocalDate today=LocalDate.now();
        String key=buildKey(userId,today);
        int offset=today.getDayOfMonth()-1;
        try{
            Boolean isSigned=redis.opsForValue().setBit(key,offset,true);
            if(Boolean.TRUE.equals(isSigned))return false;
            redis.expire(key,SIGN_TTL);
            return true;
        }catch(Exception e){
            log.warn("userId={}签到失败，快去修复",userId,e);
            throw new BusinessException("签到失败，请稍后重试");
        }
    }

    public Map<String,Object> getSignStatus(Long userId){
        LocalDate today=LocalDate.now();
        String key=buildKey(userId,today);
        int dayOfMonth=today.getDayOfMonth();
        int daysInMonth=YearMonth.from(today).lengthOfMonth();

        try{
            Long total=redis.execute((RedisCallback<Long>) conn->
                    conn.bitCount(key.getBytes(StandardCharsets.UTF_8)));
            List<Integer> days=new ArrayList<>();
            for(int day=1;day<=dayOfMonth;day++){
                //只查今天及以前
                Boolean isSigned=redis.opsForValue().getBit(key,day-1);
                if(Boolean.TRUE.equals(isSigned))days.add(day);
            }

            Map<String,Object> result=new LinkedHashMap<>();
            result.put("total",total==null?0:total);
            result.put("days",days);
            result.put("todaySigned",days.contains(dayOfMonth));
            result.put("daysInMonth",daysInMonth);
            return result;
        }catch(Exception e){
            log.warn("userId={}查询签到状态失败，快去修复",userId,e);
            return Map.of("total",0,"days",List.of(),
                    "todaySigned",false,"daysInMonth",daysInMonth);
        }
    }

    private String buildKey(Long userId,LocalDate date){
        return SIGN_KEY+userId+":"+date.format(MONTH_FMT);
    }
}
