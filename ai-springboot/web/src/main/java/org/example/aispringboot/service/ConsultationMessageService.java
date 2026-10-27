package org.example.aispringboot.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.example.aispringboot.DTO.response.ConsultationMessageResponseDTO;
import org.example.aispringboot.entity.ConsultationMessage;
import org.example.aispringboot.mapper.ConsultationMessageMapper;
import org.example.aispringboot.service.convert.ConsultationConvert;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class ConsultationMessageService {
    @Autowired
    private ConsultationMessageMapper consultationMessageMapper;

    public ConsultationMessage saveUserMessage(Long sessionId,String content,String emotionTag){
        ConsultationMessage userMessage=ConsultationMessage.builder()
                .sessionId(sessionId)
                .senderType(1)//一开始就是用户
                .messageType(1)
                .content(content)
                .emotionTag(emotionTag)
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
        return lastMessage!=null?ConsultationConvert.messageToResponse(lastMessage):null;
    }
}
