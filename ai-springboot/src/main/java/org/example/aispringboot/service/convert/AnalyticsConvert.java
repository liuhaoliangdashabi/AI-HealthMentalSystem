package org.example.aispringboot.service.convert;

import org.example.aispringboot.DTO.response.AnalyticsOverviewResponseDTO;
import org.example.aispringboot.entity.ConsultationSession;
import org.example.aispringboot.entity.EmotionDiary;
import org.example.aispringboot.entity.User;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/** 数据分析模块的转换方法。 */
public class AnalyticsConvert {

    public static AnalyticsOverviewResponseDTO toResponse(AnalyticsOverviewResponseDTO.SystemOverview systemOverview,
                                                          AnalyticsOverviewResponseDTO.ConsultationStats consultationStats,
                                                          List<AnalyticsOverviewResponseDTO.EmotionTrendItem> emotionTrend,
                                                          List<AnalyticsOverviewResponseDTO.UserActivityItem> userActivity) {
        return AnalyticsOverviewResponseDTO.builder()
                .systemOverview(systemOverview)
                .consultationStats(consultationStats)
                .emotionTrend(emotionTrend)
                .userActivity(userActivity)
                .build();
    }

    public static AnalyticsOverviewResponseDTO.SystemOverview toSystemOverview(int totalUsers, int activeUsers,
                                                                               int totalDiaries, long todayNewDiaries,
                                                                               int totalSessions, long todayNewSessions,
                                                                               Double avgMoodScore) {
        return AnalyticsOverviewResponseDTO.SystemOverview.builder()
                .totalUsers((long) totalUsers)
                .activeUsers((long) activeUsers)
                .totalDiaries((long) totalDiaries)
                .todayNewDiaries(todayNewDiaries)
                .totalSessions((long) totalSessions)
                .todayNewSessions(todayNewSessions)
                .avgMoodScore(avgMoodScore)
                .build();
    }

    public static AnalyticsOverviewResponseDTO.ConsultationStats toConsultationStats(int totalSessions,
                                                                                     Double avgDurationMinutes,
                                                                                     List<AnalyticsOverviewResponseDTO.DailyTrendItem> dailyTrend) {
        return AnalyticsOverviewResponseDTO.ConsultationStats.builder()
                .totalSessions((long) totalSessions)
                .avgDurationMinutes(avgDurationMinutes)
                .dailyTrend(dailyTrend)
                .build();
    }

    //咨询每日趋势：当天会话数与参与用户数
    public static AnalyticsOverviewResponseDTO.DailyTrendItem toDailyTrendItem(LocalDate date,
                                                                               List<ConsultationSession> sessions) {
        return AnalyticsOverviewResponseDTO.DailyTrendItem.builder()
                .date(date.toString())
                .sessionCount((long) sessions.size())
                .userCount(sessions.stream().map(ConsultationSession::getUserId).distinct().count())
                .build();
    }

    //情绪趋势：当天平均情绪分与记录数
    public static AnalyticsOverviewResponseDTO.EmotionTrendItem toEmotionTrendItem(LocalDate date,
                                                                                   List<EmotionDiary> diaries) {
        Double avgMoodScore = diaries.isEmpty() ? null
                : round1(diaries.stream().mapToInt(EmotionDiary::getMoodScore).average().orElse(0));
        return AnalyticsOverviewResponseDTO.EmotionTrendItem.builder()
                .date(date.toString())
                .avgMoodScore(avgMoodScore)
                .recordCount((long) diaries.size())
                .build();
    }

    //用户活跃度：当天活跃/新增/日记/咨询四类用户数
    public static AnalyticsOverviewResponseDTO.UserActivityItem toUserActivityItem(LocalDate date, Set<Long> activeUsers,
                                                                                   List<User> newUsers, Set<Long> diaryUsers,
                                                                                   Set<Long> consultationUsers) {
        return AnalyticsOverviewResponseDTO.UserActivityItem.builder()
                .date(date.toString())
                .activeUsers((long) activeUsers.size())
                .newUsers((long) newUsers.size())
                .diaryUsers((long) diaryUsers.size())
                .consultationUsers((long) consultationUsers.size())
                .build();
    }

    //保留一位小数
    private static Double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
