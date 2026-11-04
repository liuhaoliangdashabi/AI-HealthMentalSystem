package org.example.aispringboot.service.convert;

import org.example.aispringboot.DTO.response.AnalyticsOverviewResponseDTO;
import org.example.aispringboot.DTO.response.ConsultStatDTO;
import org.example.aispringboot.DTO.response.DiaryStatDTO;
import org.example.aispringboot.entity.ConsultationSession;
import org.example.aispringboot.entity.EmotionDiary;

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

    public static AnalyticsOverviewResponseDTO.SystemOverview toSystemOverview(long totalUsers, long activeUsers,
                                                                               long totalDiaries, long todayNewDiaries,
                                                                               long totalSessions, long todayNewSessions,
                                                                               Double avgMoodScore) {
        return AnalyticsOverviewResponseDTO.SystemOverview.builder()
                .totalUsers(totalUsers)
                .activeUsers( activeUsers)
                .totalDiaries( totalDiaries)
                .todayNewDiaries(todayNewDiaries)
                .totalSessions( totalSessions)
                .todayNewSessions(todayNewSessions)
                .avgMoodScore(avgMoodScore)
                .build();
    }

    public static AnalyticsOverviewResponseDTO.ConsultationStats toConsultationStats(long totalSessions,
                                                                                     Double avgDurationMinutes,
                                                                                     List<AnalyticsOverviewResponseDTO.DailyTrendItem> dailyTrend) {
        return AnalyticsOverviewResponseDTO.ConsultationStats.builder()
                .totalSessions(totalSessions)
                .avgDurationMinutes(avgDurationMinutes)
                .dailyTrend(dailyTrend)
                .build();
    }

    //咨询每日趋势：当天会话数与参与用户数
    public static AnalyticsOverviewResponseDTO.DailyTrendItem toDailyTrendItem(LocalDate date,
                                                                               ConsultStatDTO.Daily sessions) {
        return AnalyticsOverviewResponseDTO.DailyTrendItem.builder()
                .date(date.toString())
                .sessionCount(sessions==null?0L:sessions.getCount())
                .userCount(sessions==null?0L:sessions.getUserCount())
                .build();
    }

    //情绪趋势：当天平均情绪分与记录数
    public static AnalyticsOverviewResponseDTO.EmotionTrendItem toEmotionTrendItem(LocalDate date,
                                                                                   DiaryStatDTO.Daily diaries) {
        return AnalyticsOverviewResponseDTO.EmotionTrendItem.builder()
                .date(date.toString())
                .avgMoodScore(diaries==null?null:diaries.getAvgMoodScore())
                .recordCount(diaries==null?0L:diaries.getCount())
                .build();
    }

    //用户活跃度：当天活跃/新增/日记/咨询四类用户数
    public static AnalyticsOverviewResponseDTO.UserActivityItem toUserActivityItem(LocalDate date,long activeUsers,
                                                                                   long newUsers,long diaryUsers,long consultUsers) {
        return AnalyticsOverviewResponseDTO.UserActivityItem.builder()
                .date(date.toString())
                .activeUsers(activeUsers)
                .newUsers(newUsers)
                .diaryUsers(diaryUsers)
                .consultationUsers(consultUsers)
                .build();
    }

    //保留一位小数
    private static Double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
