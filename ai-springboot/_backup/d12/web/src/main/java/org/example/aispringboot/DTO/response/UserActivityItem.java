package org.example.aispringboot.DTO.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * 个人主页贡献日历用的最小颗粒：一天一条，只带渲染格子需要的三个字段。
 * 不复用 DiaryResponseDTO —— 那个会连日记正文一起吐出来，90 天的正文没必要发给前端。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserActivityItem {
    private LocalDate diaryDate;
    private Integer moodScore;
    private String dominantEmotion;
}
