package org.example.aispringboot.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("emotion_diary")
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmotionDiary {

    @TableId(type = IdType.AUTO)
    private Long id;

    //Long 类型要用 @NotNull
    @NotNull(message = "用户不能为空")
    @TableField("user_id")
    private Long userId;

    //数据库有 UNIQUE(user_id, diary_date)——一个用户一天只能写一篇
    @NotNull(message = "日记日期不能为空")
    @PastOrPresent(message = "日记日期不能是将来日期")
    @TableField("diary_date")
    private LocalDate diaryDate;

    //情绪评分 1~10
    @NotNull(message = "情绪评分不能为空")
    @Min(value = 1, message = "情绪评分最低为1")
    @Max(value = 10, message = "情绪评分最高为10")
    @TableField("mood_score")
    private Integer moodScore;

    //主要情绪，如 "焦虑"
    @TableField("dominant_emotion")
    @Size(max = 50,message = "主要情绪不能超过50个字符")
    private String dominantEmotion;

    @Size(max = 300, message = "情绪触发因素不能超过300个字符")
    @TableField("emotion_triggers")
    private String emotionTriggers;

    @Size(max = 300, message = "日记内容不能超过300个字符")
    @TableField("diary_content")
    private String diaryContent;

    //睡眠质量 1~5
    @Min(value = 1, message = "睡眠质量最低为1")
    @Max(value = 5, message = "睡眠质量最高为5")
    @TableField("sleep_quality")
    private Integer sleepQuality;

    //压力水平 1~5
    @Min(value = 1, message = "压力水平最低为1")
    @Max(value = 5, message = "压力水平最高为5")
    @TableField("stress_level")
    private Integer stressLevel;

    //AI情绪分析结果，JSON 字符串
    @TableField("ai_emotion_analysis")
    private String aiEmotionAnalysis;

    @TableField("ai_analysis_updated_at")
    private LocalDateTime aiAnalysisUpdatedAt;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

    //==================== 封装方法 ====================

    //情绪等级的中文描述（1~10 分）
    public String getMoodLevel() {
        if (moodScore == null) {
            return "未填写";
        }
        if (moodScore <= 2) return "很差";
        if (moodScore <= 4) return "较差";
        if (moodScore <= 6) return "一般";
        if (moodScore <= 8) return "良好";
        return "优秀";
    }

    //睡眠质量中文（1~5）
    public String getSleepQualityDisplayName() {
        if (sleepQuality == null) {
            return "未填写";
        }
        switch (sleepQuality) {
            case 1:  return "很差";
            case 2:  return "较差";
            case 3:  return "一般";
            case 4:  return "良好";
            case 5:  return "很好";
            default: return "未知";
        }
    }

    //压力水平中文（1~5，数值越高压力越大）
    public String getStressLevelDisplayName() {
        if (stressLevel == null) {
            return "未填写";
        }
        switch (stressLevel) {
            case 1:  return "很低";
            case 2:  return "较低";
            case 3:  return "一般";
            case 4:  return "较高";
            case 5:  return "很高";
            default: return "未知";
        }
    }

    //是否已经跑过 AI 分析
    public boolean hasAiAnalysis() {
        return aiEmotionAnalysis != null && !aiEmotionAnalysis.trim().isEmpty();
    }

    //情绪是否偏低——粗筛，真正的风险判断要看 AI 分析里的 riskLevel
    public boolean isLowMood() {
        return moodScore != null && moodScore <= 4;
    }
}
