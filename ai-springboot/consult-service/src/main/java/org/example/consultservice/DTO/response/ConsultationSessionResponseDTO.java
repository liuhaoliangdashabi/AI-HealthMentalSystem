package org.example.consultservice.DTO.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Data
public class ConsultationSessionResponseDTO {
    // 会话ID
    private Long id;

    // 用户ID
    private Long userId;

    // 用户昵称
    private String userNickname;

    // 会话标题
    private String sessionTitle;

    // 开始时间
    private LocalDateTime startedAt;

    // 最后一条消息内容
    private String lastMessageContent;

    // 消息数量
    private Integer messageCount;

    // 最后一条消息时间
    private LocalDateTime lastMessageTime;

    // 会话时长（分钟）= 最后一条消息时间 − 开始时间。没有消息时为 null
    private Integer durationMinutes;

    // 最后情绪分析结果
    private String lastEmotionAnalysis;

    // 最后情绪分析更新时间
    private LocalDateTime lastEmotionUpdatedAt;
}