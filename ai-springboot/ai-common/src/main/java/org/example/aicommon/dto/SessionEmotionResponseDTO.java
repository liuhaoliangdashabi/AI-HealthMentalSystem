package org.example.aicommon.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 模型返回的结构化情绪结果，web 的会话分析和 diary-service 的日记分析共用。
 * 名字带 Session 是历史包袱，两个域都用这套字段，改名会牵动两边，先不动。
 */
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
     * 这条数据是不是真的分析过。
     * 没分析时其他字段被 normalize() 填成了默认值（强度 50、风险 0、非负面），前端光看数据分不出
     * 默认值和模型真的判成中性 50。有了它前端可以直接问「分析完了吗」，不用靠值变没变来猜。
     */
    private Boolean analyzed;
}
