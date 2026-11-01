package org.example.userservice.service;

import lombok.extern.slf4j.Slf4j;
import org.example.common.BusinessException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
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
            //必须拿原始字节：opsForValue().get() 会按 UTF-8 解码，位图字节(0x80/0xC0 之类)
            //解码再编码回来已经不是原值，解析出来的 days 永远是空
            byte[] bits=redis.execute((RedisCallback<byte[]>) conn->
                    conn.stringCommands().get(key.getBytes(StandardCharsets.UTF_8)));
            if(bits!=null){
                for(int day=1;day<=dayOfMonth;day++){
                    int i=day-1;
                    //位图长度只到"最后一个签到日"，后半段根本没有字节，必须先判长度
                    if(i/8<bits.length&&((bits[i/8]>>(7-i%8))&1)==1)days.add(day);
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
