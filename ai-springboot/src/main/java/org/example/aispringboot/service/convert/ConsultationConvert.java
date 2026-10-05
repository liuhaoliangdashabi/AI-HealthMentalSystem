package org.example.aispringboot.service.convert;

import org.example.aispringboot.DTO.response.ConsultationMessageResponseDTO;
import org.example.aispringboot.DTO.response.ConsultationSessionResponseDTO;
import org.example.aispringboot.entity.ConsultationMessage;
import org.example.aispringboot.entity.ConsultationSession;

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
