package org.example.aispringboot.DTO.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalyticsOverviewResponseDTO {
    private SystemOverview systemOverview;
    private ConsultationStats consultationStats;
    private List<EmotionTrendItem> emotionTrend;
    private List<UserActivityItem> userActivity;

    //系统总览
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SystemOverview {
        private Long totalUsers;
        private Long activeUsers;
        private Long totalDiaries;
        private Long todayNewDiaries;
        private Long totalSessions;
        private Long todayNewSessions;
        private Double avgMoodScore;
    }

    //咨询会话统计
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConsultationStats {
        private Long totalSessions;
        private Double avgDurationMinutes;
        private List<DailyTrendItem> dailyTrend;
    }

    //咨询每日趋势
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DailyTrendItem {
        private String date;
        private Long sessionCount;
        private Long userCount;
    }

    //情绪趋势
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EmotionTrendItem {
        private String date;
        private Double avgMoodScore;
        private Long recordCount;
    }

    //用户活跃度趋势
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserActivityItem {
        private String date;
        private Long activeUsers;
        private Long newUsers;
        private Long diaryUsers;
        private Long consultationUsers;
    }
}
