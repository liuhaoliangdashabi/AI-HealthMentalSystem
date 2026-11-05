package org.example.aispringboot.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.example.aispringboot.DTO.response.*;
import org.example.aispringboot.DTO.response.AnalyticsOverviewResponseDTO.ConsultationStats;
import org.example.aispringboot.DTO.response.AnalyticsOverviewResponseDTO.DailyTrendItem;
import org.example.aispringboot.DTO.response.AnalyticsOverviewResponseDTO.EmotionTrendItem;
import org.example.aispringboot.DTO.response.AnalyticsOverviewResponseDTO.SystemOverview;
import org.example.aispringboot.DTO.response.AnalyticsOverviewResponseDTO.UserActivityItem;
import org.example.aispringboot.entity.ConsultationMessage;
import org.example.aispringboot.entity.ConsultationSession;
import org.example.aispringboot.entity.EmotionDiary;
import org.example.aispringboot.mapper.ConsultationMessageMapper;
import org.example.aispringboot.mapper.ConsultationSessionMapper;
import org.example.aispringboot.mapper.EmotionDiaryMapper;
import org.example.aispringboot.service.convert.AnalyticsConvert;
import org.example.aispringboot.util.RedisKeys;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.cert.LDAPCertStoreParameters;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
    private EmotionDiaryMapper emotionDiaryMapper;
    @Autowired
    private ConsultationSessionMapper consultationSessionMapper;
    @Autowired
    private ConsultationMessageMapper consultationMessageMapper;
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

    public List<UvTrendItem> getUVTrend(){
        LocalDate today= LocalDate.now();
        LocalDate since=today.minusDays(RECENT_DAYS-1L);
        Map<LocalDate,Long> uvByDay=new LinkedHashMap<>();
        List<LocalDate> needDb=new ArrayList<>();
        boolean redisDown=false;
        for(LocalDate day=since;!day.isAfter(today);day=day.plusDays(1)){
            String key= RedisKeys.uv(day);
            Long count;
            try{
                count=redis.opsForHyperLogLog().size(key);
                if (count==0L) {
                    boolean hasData=Boolean.TRUE.equals(redis.hasKey(key));
                    boolean knownEmpty=Boolean.TRUE.equals(redis.hasKey(RedisKeys.uvEmpty(day)));
                    //可能真没人访问，也可能key不存在
                    if(!hasData && !knownEmpty){
                        needDb.add(day);
                        continue;
                    }
                }

            }catch(Exception e){
                log.warn("Redis读取失败，{}起剩余天数整段回源",day,e);
                redisDown=true;
                for(LocalDate d=day;!d.isAfter(today);d=d.plusDays(1)){
                    needDb.add(d);
                }
                break;
            }
            uvByDay.put(day,count);
        }

        if(!needDb.isEmpty()){
            Map<LocalDate,Set<Long>> detail = queryUvDetailFromDb(needDb);
            for(LocalDate day:needDb){
                Set<Long> userIds=detail.getOrDefault(day,Set.of());
                uvByDay.put(day,(long)userIds.size());
                if (!redisDown) {
                    writeBackToRedis(day,userIds);
                }
            }
        }

        List<UvTrendItem> trend=new ArrayList<>();
        for(LocalDate day=since;!day.isAfter(today);day=day.plusDays(1)){
            UvTrendItem item=new UvTrendItem();
            item.setDate(day.toString());
            item.setActiveUsers(uvByDay.getOrDefault(day,0L));
            trend.add(item);
        }
        return trend;
    }

    private Map<LocalDate,Set<Long>> queryUvDetailFromDb(List<LocalDate> days){
        Map<LocalDate,Set<Long>> byDay=new HashMap<>();
        LocalDate min=days.get(0);
        LocalDate max=days.get(days.size()-1);
        emotionDiaryMapper.selectList(new LambdaQueryWrapper<EmotionDiary>().
                        ge(EmotionDiary::getDiaryDate,min).
                        le(EmotionDiary::getDiaryDate,max))
                .forEach(d->byDay.computeIfAbsent(d.getDiaryDate(),k->new HashSet<>())
                        .add(d.getUserId()));
        consultationSessionMapper.selectList(new LambdaQueryWrapper<ConsultationSession>()
                .ge(ConsultationSession::getStartedAt,min.atStartOfDay())
                .lt(ConsultationSession::getStartedAt,max.plusDays(1).atStartOfDay()))
                .forEach(s->byDay.computeIfAbsent(s.getStartedAt().toLocalDate(),
                                k->new HashSet<>())
                        .add(s.getUserId()));
        return byDay;
    }

    private void writeBackToRedis(LocalDate day,Set<Long> userIds){
        try{
            if(userIds.isEmpty()){
                redis.opsForValue().set(RedisKeys.uvEmpty(day),"1",RedisKeys.UV_TTL);
                return;
            }
            String key=RedisKeys.uv(day);
            redis.opsForHyperLogLog().add(key,
                    userIds.stream().map(String::valueOf).toArray(String[]::new));

            redis.expire(key,RedisKeys.UV_TTL);
        }catch(Exception e){
            log.warn("UV回存失败，date={}",day,e);
        }
    }

    private Double calcAvgDurationMinutes(List<ConsultationSession> sessions,
                                          List<ConsultationMessage> messages) {
        //每个会话最后一条消息的时间
        Map<Long, Optional<LocalDateTime>> lastMessageAt = messages.stream()
                .collect(Collectors.groupingBy(ConsultationMessage::getSessionId,
                        Collectors.mapping(ConsultationMessage::getCreatedAt,
                                Collectors.maxBy(Comparator.naturalOrder()))));

        List<Long> durationMinutes = sessions.stream()
                .map(s -> lastMessageAt.get(s.getId())
                        .map(last -> Duration.between(s.getStartedAt(), last).toMinutes())
                        .orElse(null))
                .filter(Objects::nonNull)
                .toList();

        return durationMinutes.isEmpty() ? null
                : round1(durationMinutes.stream().mapToLong(Long::longValue).average().orElse(0));
    }

    //保留一位小数
    private static Double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
    //判空
    private static long nvl(Long v){
        return v==null?0L:v;
    }
}
