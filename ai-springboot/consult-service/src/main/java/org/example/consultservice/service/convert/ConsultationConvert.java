package org.example.consultservice.service.convert;


import org.example.consultservice.DTO.response.ConsultationMessageResponseDTO;
import org.example.consultservice.DTO.response.ConsultationSessionResponseDTO;
import org.example.consultservice.entity.ConsultationMessage;
import org.example.consultservice.entity.ConsultationSession;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 心理咨询模块的转换方法。
 * AB端（管理端）和C端（用户端）共用同一套 —— 转换只跟实体和DTO有关，跟谁在调无关。
 */
public class ConsultationConvert {

    public static ConsultationSessionResponseDTO sessionToResponse(ConsultationSession session,
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
                //会话时长 = 最后一条消息 − 开始时间。没有消息时为 null（前端会显示 0分钟）
                .durationMinutes(session.getStartedAt()==null || lastMessageTime==null ? null
                        : (int)Duration.between(session.getStartedAt(),lastMessageTime).toMinutes())
                .build();
    }

    public static ConsultationMessageResponseDTO messageToResponse(ConsultationMessage message) {
        ConsultationMessageResponseDTO dto = ConsultationMessageResponseDTO.builder()
                .id(message.getId())
                .sessionId(message.getSessionId())
                .senderType(message.getSenderType())
                .senderTypeDesc(message.getSenderTypeDesc())
                .messageType(message.getMessageType())
                .messageTypeDesc(message.getMessageTypeDesc())
                .content(message.getContent())
                .emotionTag(message.getEmotionTag())
                .aiModel(message.getAiModel())
                .createdAt(message.getCreatedAt())
                .build();
        dto.calculateContentLength();
        return dto;
    }
}
