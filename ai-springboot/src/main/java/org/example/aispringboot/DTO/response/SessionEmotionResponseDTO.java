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
public class SessionEmotionResponseDTO {
    //主要情绪，如 "中性"
    private String primaryEmotion;

    //情绪强度 0~100
    private Integer emotionScore;

    //是否负面情绪
    private Boolean isNegative;

    //风险等级 0:正常 1:关注 2:预警 3:危机
    private Integer riskLevel;

    //给用户的小建议
    private String suggestion;

    //风险描述
    private String riskDescription;

    //治愈行动建议
    private List<String> improvementSuggestions;
}
