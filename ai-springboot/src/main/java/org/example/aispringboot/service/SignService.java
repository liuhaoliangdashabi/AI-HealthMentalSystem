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
            if(!Boolean.TRUE.equals(redis.expire(key,SIGN_TTL))) {
                log.warn("签到key设过期失败，key={}",key);
            }
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
                    conn.stringCommands().bitCount(key.getBytes(StandardCharsets.UTF_8)));
            List<Integer> days=new ArrayList<>();
//            for(int day=1;day<=dayOfMonth;day++){
//                //只查今天及以前
//                Boolean isSigned=redis.opsForValue().getBit(key,day-1);
//                if(Boolean.TRUE.equals(isSigned))days.add(day);
//            }
            String raw=redis.opsForValue().get(key);
//            List<Integer> redisGetDays=new ArrayList<>();
            if(raw!=null){
                byte[] bits=raw.getBytes(StandardCharsets.ISO_8859_1);
                for(int day=1;day<=dayOfMonth;day++){
                    int i=day-1;
                    //(仿)一个位图
                    if(((bits[i/8]>>(7-i%8))&1)==1)days.add(day);
                }
            }

            return buildSignResult(total==null?0L:total,
                    days,dayOfMonth,daysInMonth);
        }catch(Exception e){
            log.warn("userId={}查询签到状态失败，快去修复",userId,e);
            return buildSignResult(0L,List.of(),
                    dayOfMonth,daysInMonth);
        }
    }

    private String buildKey(Long userId,LocalDate date){
        return SIGN_KEY+userId+":"+date.format(MONTH_FMT);
    }

    private Map<String,Object> buildSignResult(long total,List<Integer> days,
                                               int dayOfMonth,int daysInMonth){
        Map<String,Object> result=new LinkedHashMap<>();
        result.put("total",total);
        result.put("days",days);
        result.put("todaySigned",days.contains(dayOfMonth));
        result.put("daysInMonth",daysInMonth);
        return result;
    }

}
