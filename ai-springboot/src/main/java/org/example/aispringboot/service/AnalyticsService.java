package org.example.aispringboot.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.example.aispringboot.DTO.response.AnalyticsOverviewResponseDTO;
import org.example.aispringboot.DTO.response.AnalyticsOverviewResponseDTO.ConsultationStats;
import org.example.aispringboot.DTO.response.AnalyticsOverviewResponseDTO.DailyTrendItem;
import org.example.aispringboot.DTO.response.AnalyticsOverviewResponseDTO.EmotionTrendItem;
import org.example.aispringboot.DTO.response.AnalyticsOverviewResponseDTO.SystemOverview;
import org.example.aispringboot.DTO.response.AnalyticsOverviewResponseDTO.UserActivityItem;
import org.example.aispringboot.DTO.response.UvTrendItem;
import org.example.aispringboot.entity.ConsultationMessage;
import org.example.aispringboot.entity.ConsultationSession;
import org.example.aispringboot.entity.EmotionDiary;
import org.example.aispringboot.entity.User;
import org.example.aispringboot.mapper.ConsultationMessageMapper;
import org.example.aispringboot.mapper.ConsultationSessionMapper;
import org.example.aispringboot.mapper.EmotionDiaryMapper;
import org.example.aispringboot.mapper.UserMapper;
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
    private UserMapper userMapper;
    @Autowired
    private EmotionDiaryMapper emotionDiaryMapper;
    @Autowired
    private ConsultationSessionMapper consultationSessionMapper;
    @Autowired
    private ConsultationMessageMapper consultationMessageMapper;
    @Autowired
    private StringRedisTemplate redis;

    /**
     * 管理端数据看板总览。
     *
     * <p><b>它有多贵：</b>四张表 {@code selectList(null)} 全量捞进内存再聚合 ——
     * user / emotion_diary / consultation_session / consultation_message 全表扫描。
     * 只要有人点开看板首页，就按表的行数线性烧 CPU 和 GC。
     *
     * <p><b>为什么缓存能救：</b>看板是「多人看、几乎没人写」的典型只读视图。
     * 10 个管理员同时刷新，不加缓存就是 10 次全表扫描，加了就是 1 次 + 9 次 GET。
     *
     * <p><b>为什么 TTL 只有 60 秒（见 CacheConfig 里的独立配置）：</b>
     * 看板的语义是"看现在大概什么样"，60 秒的延迟没人感知得到；
     * 但沿用全局的 30 分钟，运营改完数据半小时后看板上还是老数字，会以为系统坏了。
     * <b>缓存 TTL 不是"越长越好"，是按「业务能容忍多旧」定的。</b>
     *
     * <p><b>⚠️ 缓存只是遮羞布：</b>真正的解法是让数据库做聚合
     * （{@code SELECT COUNT(*) ... GROUP BY DATE(created_at)}），别把全表拉进 JVM。
     * 现在表小所以这么写没炸，数据量一上来缓存也救不了 —— 缓存降低的是频率，不是单次成本。
     */
    @Cacheable(cacheNames = "analytics", key = "'overview'")
    public AnalyticsOverviewResponseDTO getOverview() {
        LocalDate today = LocalDate.now();
        LocalDate since = today.minusDays(RECENT_DAYS - 1L);

        //数据量不大，直接读库全量拉取、在内存里聚合，不写GROUP BY
        List<User> users = userMapper.selectList(null);
        List<EmotionDiary> diaries = emotionDiaryMapper.selectList(null);
        List<ConsultationSession> sessions = consultationSessionMapper.selectList(null);
        List<ConsultationMessage> messages = consultationMessageMapper.selectList(null);

        //近30天窗口
        List<EmotionDiary> windowDiaries = diaries.stream()
                .filter(d -> !d.getDiaryDate().isBefore(since))
                .toList();
        List<ConsultationSession> windowSessions = sessions.stream()
                .filter(s -> !s.getStartedAt().toLocalDate().isBefore(since))
                .toList();

        //活跃用户：近30天写过日记或发起过咨询
        Set<Long> activeUserIds = new HashSet<>();
        windowDiaries.forEach(d -> activeUserIds.add(d.getUserId()));
        windowSessions.forEach(s -> activeUserIds.add(s.getUserId()));

        //今日新增
        long todayNewDiaries = diaries.stream()
                .filter(d -> d.getDiaryDate().equals(today)).count();
        long todayNewSessions = sessions.stream()
                .filter(s -> s.getStartedAt().toLocalDate().equals(today)).count();

        //全量平均情绪分，无日记时为null
        Double avgMoodScore = diaries.isEmpty() ? null
                : round1(diaries.stream().mapToInt(EmotionDiary::getMoodScore).average().orElse(0));

        //平均会话时长：每个会话最后一条消息时间 - 会话开始时间
        Double avgDurationMinutes = calcAvgDurationMinutes(sessions, messages);

        //按自然日分组
        Map<LocalDate, List<EmotionDiary>> diariesByDate = windowDiaries.stream()
                .collect(Collectors.groupingBy(EmotionDiary::getDiaryDate));
        Map<LocalDate, List<ConsultationSession>> sessionsByDate = windowSessions.stream()
                .collect(Collectors.groupingBy(s -> s.getStartedAt().toLocalDate()));
        Map<LocalDate, List<User>> newUsersByDate = users.stream()
                .filter(u -> !u.getCreatedAt().toLocalDate().isBefore(since))
                .collect(Collectors.groupingBy(u -> u.getCreatedAt().toLocalDate()));

        //补齐30天里没有记录的日期
        List<EmotionTrendItem> emotionTrend = new ArrayList<>();
        List<DailyTrendItem> dailyTrend = new ArrayList<>();
        List<UserActivityItem> userActivity = new ArrayList<>();
        for (LocalDate d = since; !d.isAfter(today); d = d.plusDays(1)) {
            List<EmotionDiary> dayDiaries = diariesByDate.getOrDefault(d, List.of());
            List<ConsultationSession> daySessions = sessionsByDate.getOrDefault(d, List.of());

            Set<Long> diaryUserIds;
            Set<Long> consultationUserIds;
            Set<Long> dayActiveUserIds;
                diaryUserIds = dayDiaries.stream()
                        .map(EmotionDiary::getUserId).collect(Collectors.toSet());
                consultationUserIds = daySessions.stream()
                        .map(ConsultationSession::getUserId).collect(Collectors.toSet());
                dayActiveUserIds = new HashSet<>();
                dayActiveUserIds.addAll(diaryUserIds);
                dayActiveUserIds.addAll(consultationUserIds);


            emotionTrend.add(AnalyticsConvert.toEmotionTrendItem(d, dayDiaries));
            dailyTrend.add(AnalyticsConvert.toDailyTrendItem(d, daySessions));
            userActivity.add(AnalyticsConvert.toUserActivityItem(d, dayActiveUserIds,
                    newUsersByDate.getOrDefault(d, List.of()), diaryUserIds, consultationUserIds));
        }

        SystemOverview systemOverview = AnalyticsConvert.toSystemOverview(
                users.size(), activeUserIds.size(), diaries.size(), todayNewDiaries,
                sessions.size(), todayNewSessions, avgMoodScore);
        ConsultationStats consultationStats = AnalyticsConvert.toConsultationStats(
                sessions.size(), avgDurationMinutes, dailyTrend);

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
}
