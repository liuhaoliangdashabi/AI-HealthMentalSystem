package org.example.diaryservice.DTO.command;

import lombok.Data;

import java.time.LocalDate;

@Data
public class DiaryCommandDTO {
    @NotNull(message = "日记日期不能为空")
    @PastOrPresent(message = "日记日期不能是将来日期")
    private LocalDate diaryDate;

    @NotNull(message = "情绪评分不能为空")
    @Min(value = 1, message = "情绪评分最低为1")
    @Max(value = 10, message = "情绪评分最高为10")
    private Integer moodScore;

    @Size(max = 50, message = "主要情绪不能超过50个字符")
    private String dominantEmotion;

    @Size(max = 300, message = "情绪触发因素不能超过300个字符")
    private String emotionTriggers;

    @Size(max = 300, message = "日记内容不能超过300个字符")
    private String diaryContent;

    @Min(value = 1, message = "睡眠质量最低为1")
    @Max(value = 5, message = "睡眠质量最高为5")
    private Integer sleepQuality;

    @Min(value = 1, message = "压力水平最低为1")
    @Max(value = 5, message = "压力水平最高为5")
    private Integer stressLevel;
}
