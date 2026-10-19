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

    /**
     * 这条数据是不是「真的分析过」。
     *
     * <p>为什么必须有这个字段：没分析的时候其他字段都被 {@code normalize()} 填成了默认值
     * （强度 50、风险 0、非负面），前端<b>光看数据分不出「默认值」和「模型真的判成中性 50」</b>。
     * 于是轮询只能靠"值变没变"来判断，而默认值里 {@code primaryEmotion} 是 null，
     * 第一次轮询就把 null 写进去 → 前端以为"变了" → 立刻停止轮询。
     *
     * <p>加了它，前端就能问一句「分析完了吗」，而不是猜。
     */
    private Boolean analyzed;
}
