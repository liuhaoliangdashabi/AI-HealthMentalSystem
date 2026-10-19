package org.example.aispringboot.service.convert;

import org.example.aispringboot.DTO.command.DiaryCommandDTO;
import org.example.aispringboot.DTO.response.DiaryResponseDTO;
import org.example.aispringboot.entity.EmotionDiary;

import java.time.LocalDateTime;

/** 情绪日记模块的转换方法。 */
public class DiaryConvert {

    public static EmotionDiary commandToEntity(DiaryCommandDTO commandDTO, Long userId) {
        return EmotionDiary.builder()
                .userId(userId)
                .diaryDate(commandDTO.getDiaryDate())
                .moodScore(commandDTO.getMoodScore())
                .dominantEmotion(commandDTO.getDominantEmotion())
                .emotionTriggers(commandDTO.getEmotionTriggers())
                .diaryContent(commandDTO.getDiaryContent())
                .sleepQuality(commandDTO.getSleepQuality())
                .stressLevel(commandDTO.getStressLevel())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    public static DiaryResponseDTO toResponse(EmotionDiary diary, String username, String nickname) {
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
}
