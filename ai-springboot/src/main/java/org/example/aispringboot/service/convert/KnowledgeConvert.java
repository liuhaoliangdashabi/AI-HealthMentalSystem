package org.example.aispringboot.service.convert;

import org.example.aispringboot.DTO.command.ArticleCommandDTO;
import org.example.aispringboot.DTO.response.*;
import org.example.aispringboot.entity.*;
import org.example.aispringboot.enumClass.ArticleStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

public class KnowledgeConvert {
    public static CategoryResponseDTO categoryToResponse(KnowledgeCategory c){
        return CategoryResponseDTO.builder()
                .id(c.getId())
                .categoryName(c.getCategoryName())
                .build();
    }

    public static ArticleResponseDTO articleToResponse(KnowledgeArticle a,
                                                       String authorName,
                                                       String categoryName){
        return ArticleResponseDTO.builder()
                .id(a.getId())
                .title(a.getTitle())
                .content(a.getContent())
                .coverImage(a.getCoverImage())
                .categoryId(a.getCategoryId())
                .status(a.getStatus())
                .readCount(a.getReadCount())
                .summary(a.getSummary())
                .tags(a.getTags())
                .publishedAt(a.getPublishedAt())
                .updatedAt(a.getUpdatedAt())
                .authorName(authorName)
                .categoryName(categoryName)
                .build();
    }
    public static KnowledgeArticle commandToEntity(ArticleCommandDTO cmd, Long authorId){
        return KnowledgeArticle.builder()
                .title(cmd.getTitle())
                .content(cmd.getContent())
                .coverImage(cmd.getCoverImage())
                .categoryId(cmd.getCategoryId())
                .summary(cmd.getSummary())
                .tags(cmd.getTags())
                .authorId(authorId)                       // ← 从 token 拿的
                .readCount(0)                             // ← 新文章从 0 开始
                .status(ArticleStatus.DRAFT.getCode())    // ← 新文章一律草稿
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    public static KnowledgeArticle toUpdateEntity(String id, ArticleCommandDTO cmd){
        return KnowledgeArticle.builder()
                .id(id)                                   // ← 从路径拿
                .title(cmd.getTitle())
                .content(cmd.getContent())
                .coverImage(cmd.getCoverImage())
                .categoryId(cmd.getCategoryId())
                .summary(cmd.getSummary())
                .tags(cmd.getTags())
                .updatedAt(LocalDateTime.now())
                // authorId / readCount / status / createdAt 都不填
                // → updateById 会跳过它们，保持原值
                .build();
    }


    public static ConsultationSessionResponseDTO ConsultationSessionToResponse(ConsultationSession session,
                                                                               String userNickname,
                                                                               Integer messageCount,
                                                                               String lastMessageContent,
                                                                               LocalDateTime lastMessageTime) {
        return ConsultationSessionResponseDTO.builder()
                .id(session.getId())
                .userId(session.getUserId())
                .userNickname(userNickname)
                .sessionTitle(session.getSessionTitle())
                .startedAt(session.getStartedAt())
                .lastMessageContent(lastMessageContent)
                .messageCount(messageCount)
                .lastMessageTime(lastMessageTime)
                .lastEmotionAnalysis(session.getLastEmotionAnalysis())
                .lastEmotionUpdatedAt(session.getLastEmotionUpdatedAt())
                .build();
    }

    public static ConsultationMessageResponseDTO messageToResponse(ConsultationMessage message) {
        ConsultationMessageResponseDTO dto=ConsultationMessageResponseDTO.builder()
                .id(message.getId())
                .sessionId(message.getSessionId())
                .senderType(message.getSenderType())
                .messageType(message.getMessageType())
                .content(message.getContent())
                .emotionTag(message.getEmotionTag())
                .aiModel(message.getAiModel())
                .createdAt(message.getCreatedAt())
                .build();
        dto.calculateContentLength();
        return dto;
    }

    public static DiaryResponseDTO toResponse (EmotionDiary diary,
                                               String username,
                                               String nickname) {
         return DiaryResponseDTO.builder()
            .id(diary.getId())
            .userId(diary.getUserId())
            .username(username)
            .nickname(nickname)
            .diaryDate(diary.getDiaryDate())
            .moodScore(diary.getMoodScore())
            .dominantEmotion(diary.getDominantEmotion())
            .emotionTriggers(diary.getEmotionTriggers())
            .diaryContent(diary.getDiaryContent())
            .sleepQuality(diary.getSleepQuality())
            .stressLevel(diary.getStressLevel())
            .aiEmotionAnalysis(diary.getAiEmotionAnalysis())
            .aiAnalysisUpdatedAt(diary.getAiAnalysisUpdatedAt())
            .createdAt(diary.getCreatedAt())
            .updatedAt(diary.getUpdatedAt())
            .build();
    }

    //-----------------------------------数据分析相关
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
