package org.example.aispringboot.service;

import lombok.extern.slf4j.Slf4j;
import org.example.aispringboot.DTO.response.*;
import org.example.aispringboot.DTO.response.AnalyticsOverviewResponseDTO.ConsultationStats;
import org.example.aispringboot.DTO.response.AnalyticsOverviewResponseDTO.DailyTrendItem;
import org.example.aispringboot.DTO.response.AnalyticsOverviewResponseDTO.EmotionTrendItem;
import org.example.aispringboot.DTO.response.AnalyticsOverviewResponseDTO.SystemOverview;
import org.example.aispringboot.DTO.response.AnalyticsOverviewResponseDTO.UserActivityItem;
import org.example.aispringboot.service.convert.AnalyticsConvert;
import org.example.aispringboot.util.RedisKeys;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class AnalyticsService {
    private static final int RECENT_DAYS = 30;

    @Autowired
    private UserStatClient userStatClient;
    @Autowired
    private DiaryStatClient diaryStatClient;
    @Autowired
    private ConsultStatClient consultStatClient;
    @Autowired
    private StringRedisTemplate redis;

    /**
     * 管理端数据看板总览。四张表全量捞进内存再聚合，很贵，所以必须走缓存。
     * TTL 单独配 60 秒（见 CacheConfig）：看板能容忍 60 秒延迟，30 分钟会让人以为系统坏了。
     * 缓存只是降低频率，单次全表扫描的成本没变，表大了还是得让数据库做聚合。
     */
    @Cacheable(cacheNames = "analytics", key = "'overview'")
    public AnalyticsOverviewResponseDTO getOverview() {
        UserStatDTO userStat=userStatClient.fetch(RECENT_DAYS);
        DiaryStatDTO diaryStat=diaryStatClient.fetch(RECENT_DAYS);
        ConsultStatDTO consultStat=consultStatClient.fetch(RECENT_DAYS);

        LocalDate today = LocalDate.now();
        LocalDate since = today.minusDays(RECENT_DAYS - 1L);

//        //活跃用户：近30天写过日记或发起过咨询
        String[] uvKeys=new String[RECENT_DAYS];
        for(int i=0;i<RECENT_DAYS;i++)
            uvKeys[i]=RedisKeys.uv(since.plusDays(i));
        Long totalActiveUsers=0L;
        try {
            totalActiveUsers = nvl(redis.opsForHyperLogLog().size(uvKeys));
        } catch (Exception e) {
            log.warn("AnalyticsService中Redis HyperLogLog总活跃用户失败",e);
        }

        Map<LocalDate,Long> newUsersByDate=userStat.getDailyNew().entrySet().stream()
                .collect(Collectors.toMap(e->LocalDate.parse(e.getKey()),Map.Entry::getValue));
        List<EmotionTrendItem> emotionTrend = new ArrayList<>();
        List<DailyTrendItem> dailyTrend = new ArrayList<>();
        List<UserActivityItem> userActivity = new ArrayList<>();

        for(LocalDate d=since;!d.isAfter(today);d=d.plusDays(1)){
            String k=d.toString();
            DiaryStatDTO.Daily dd=diaryStat.getDaily().get(k);
            ConsultStatDTO.Daily cd=consultStat.getDaily().get(k);
            long diaryUsers=dd==null?0:dd.getUserCount();
            long consultUsers=cd==null?0:cd.getUserCount();
            long dayActive= 0;
            try {
                dayActive = nvl(redis.opsForHyperLogLog().size(RedisKeys.uv(d)));
            } catch (Exception e) {
                log.warn("AnalyticsService中Redis HyperLogLog每日活跃人数失败",e);
            }

            emotionTrend.add(AnalyticsConvert.toEmotionTrendItem(d,dd));
            dailyTrend.add(AnalyticsConvert.toDailyTrendItem(d,cd));
            userActivity.add(AnalyticsConvert.toUserActivityItem(d,dayActive,
                    newUsersByDate.getOrDefault(d,0L),diaryUsers,consultUsers));
        }

        SystemOverview systemOverview = AnalyticsConvert.toSystemOverview(
                userStat.getTotal(), totalActiveUsers, diaryStat.getTotal(),diaryStat.getTodayNew(),
                consultStat.getTotal(),consultStat.getTodayNew(),diaryStat.getAvgMoodScore());
        ConsultationStats consultationStats = AnalyticsConvert.toConsultationStats(
                consultStat.getTotal(),consultStat.getAvgDurationMinutes(), dailyTrend);

        return AnalyticsConvert.toResponse(systemOverview, consultationStats,
                emotionTrend, userActivity);
    }

    /**
     * UV 曲线：一次请求要打 30 次 HyperLogLog，走 Redis 不算慢但也没必要每次都算。
     * 复用 analytics 缓存（TTL 60 秒），和总览保持同一个刷新节奏。
     */
    @Cacheable(cacheNames = "analytics", key = "'uv-trend'")
    public List<UvTrendItem> getUVTrend(){
        LocalDate today= LocalDate.now();
        LocalDate since=today.minusDays(RECENT_DAYS-1L);
        List<UvTrendItem> trend=new ArrayList<>();
        for(LocalDate day=since;!day.isAfter(today);day=day.plusDays(1)){
            long count=0L;
            try{
                count=nvl(redis.opsForHyperLogLog().size(RedisKeys.uv(day)));
            }catch (Exception e){
                log.warn("Redis读取UV失败，day={}按0处理",day,e);
            }
            UvTrendItem item=new UvTrendItem();
            item.setDate(day.toString());
            item.setActiveUsers(count);
            trend.add(item);
        }
        return trend;
    }

    //判空
    private static long nvl(Long v){
        return v==null?0L:v;
    }
}
