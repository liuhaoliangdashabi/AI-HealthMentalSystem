package org.example.aispringboot.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.example.aispringboot.DTO.response.ConsultationMessageResponseDTO;
import org.example.aispringboot.entity.ConsultationMessage;
import org.example.aispringboot.mapper.ConsultationMessageMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class ConsultationMessageService {
    @Autowired
    private ConsultationMessageMapper consultationMessageMapper;

    public ConsultationMessage saveUserMessage(Long sessionId,String content,String emotion_tag){
        ConsultationMessage userMessage=ConsultationMessage.builder()
                .sessionId(sessionId)
                .senderType(1)//一开始就是用户
                .messageType(1)
                .content(content)
                .emotionTag(emotion_tag)
                .createdAt(LocalDateTime.now())
                .build();
        consultationMessageMapper.insert(userMessage);
        return userMessage;

    }

    public ConsultationMessage saveAiMessage(Long sessionId,String content,String aiModel){
        ConsultationMessage message=ConsultationMessage.builder()
                .sessionId(sessionId)
                .senderType(2)
                .messageType(1)
                .content(content)
                .aiModel(aiModel)
                .createdAt(LocalDateTime.now())
                .build();

        consultationMessageMapper.insert(message);
        return message;
    }

    public Integer getMessageCountBySessionId(Long sessionId){
        LambdaQueryWrapper<ConsultationMessage> queryWrapper=new LambdaQueryWrapper<>();
        queryWrapper.eq(ConsultationMessage::getSessionId,sessionId);

        return consultationMessageMapper.selectCount(queryWrapper).intValue();
    }

    //获取会话最后一条消息
    public ConsultationMessageResponseDTO getLastMessageBySessionId(Long sessionId){
        LambdaQueryWrapper<ConsultationMessage> queryWrapper=new LambdaQueryWrapper<>();
        queryWrapper.eq(ConsultationMessage::getSessionId,sessionId)
                .orderByDesc(ConsultationMessage::getCreatedAt)
                .last("limit 1");

        ConsultationMessage lastMessage=consultationMessageMapper.selectOne(queryWrapper);
        return lastMessage!=null?convertToResponseDTO(lastMessage):null;
    }

    private ConsultationMessageResponseDTO convertToResponseDTO(ConsultationMessage message) {
        if (message == null) {
            return null;
        }

        // 手动逐字段赋值，确保转换的准确性和可控性
        ConsultationMessageResponseDTO responseDTO =ConsultationMessageResponseDTO.builder()
                .id(message.getId())
                .sessionId(message.getSessionId())
                .senderType(message.getSenderType())
                .messageType(message.getMessageType())
                .content(message.getContent())
                .emotionTag(message.getEmotionTag())
                .aiModel(message.getAiModel())
                .createdAt(message.getCreatedAt())
                .senderTypeDesc(message.getSenderTypeDesc())
                .messageTypeDesc(message.getMessageTypeDesc())
                .build();
        responseDTO.calculateContentLength();

        // 设置描述字段（通过实体方法获取）
        responseDTO.setSenderTypeDesc(message.getSenderTypeDesc());
        responseDTO.setMessageTypeDesc(message.getMessageTypeDesc());

        // 计算消息长度
        responseDTO.calculateContentLength();

        return responseDTO;
    }
}
