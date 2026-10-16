package org.example.aispringboot.service;

import lombok.extern.slf4j.Slf4j;
import org.example.aispringboot.DTO.response.AnalyticsOverviewResponseDTO;
import org.example.aispringboot.DTO.response.AnalyticsOverviewResponseDTO.ConsultationStats;
import org.example.aispringboot.DTO.response.AnalyticsOverviewResponseDTO.DailyTrendItem;
import org.example.aispringboot.DTO.response.AnalyticsOverviewResponseDTO.EmotionTrendItem;
import org.example.aispringboot.DTO.response.AnalyticsOverviewResponseDTO.SystemOverview;
import org.example.aispringboot.DTO.response.AnalyticsOverviewResponseDTO.UserActivityItem;
import org.example.aispringboot.entity.ConsultationMessage;
import org.example.aispringboot.entity.ConsultationSession;
import org.example.aispringboot.entity.EmotionDiary;
import org.example.aispringboot.entity.User;
import org.example.aispringboot.mapper.ConsultationMessageMapper;
import org.example.aispringboot.mapper.ConsultationSessionMapper;
import org.example.aispringboot.mapper.EmotionDiaryMapper;
import org.example.aispringboot.mapper.UserMapper;
import org.example.aispringboot.service.convert.AnalyticsConvert;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
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

    public AnalyticsOverviewResponseDTO getOverview() {
        LocalDate today = LocalDate.now();
        LocalDate since = today.minusDays(RECENT_DAYS - 1L);

        //数据量不大，直接走BaseMapper全量拉取、在内存里聚合，不写GROUP BY
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

            Set<Long> diaryUserIds = null;
            Set<Long> consultationUserIds = null;
            Set<Long> dayActiveUserIds = null;
            try {

            } catch (Exception e) {
                log.warn("补齐日期-redis出错，转用读库");
                diaryUserIds = dayDiaries.stream()
                        .map(EmotionDiary::getUserId).collect(Collectors.toSet());
                consultationUserIds = daySessions.stream()
                        .map(ConsultationSession::getUserId).collect(Collectors.toSet());
                dayActiveUserIds = new HashSet<>();
                dayActiveUserIds.addAll(diaryUserIds);
                dayActiveUserIds.addAll(consultationUserIds);
            }

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
