package org.example.aispringboot.AiService;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.extern.slf4j.Slf4j;
import org.example.aispringboot.DTO.response.SessionEmotionResponseDTO;
import org.example.aispringboot.entity.ConsultationMessage;
import org.example.aispringboot.entity.ConsultationSession;
import org.example.aispringboot.mapper.ConsultationMessageMapper;
import org.example.aispringboot.mapper.ConsultationSessionMapper;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import javax.print.DocFlavor;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class EmotionAnalysisService {
    @Autowired
    @Qualifier("emotion-analyzer")
    private ChatClient emotionClient;
    @Autowired
    private ConsultationMessageMapper messageMapper;
    @Autowired
    private ConsultationSessionMapper sessionMapper;

    @Async
    public void analyzeAsync(Long sessionId){
        try{
            List<ConsultationMessage> messages = messageMapper.selectList(
                    new LambdaQueryWrapper<ConsultationMessage>()
                            .eq(ConsultationMessage::getSessionId, sessionId)
                            .orderByDesc(ConsultationMessage::getCreatedAt)
                            .orderByDesc(ConsultationMessage::getId)   // id 兜底，防止同一秒乱序
                            .last("limit 20"));
            if(messages.isEmpty())return;
            Collections.reverse(messages);

            String conversation=messages.stream()
                    .map(m->(m.getSenderType()!=null && m.getSenderType()==1? "用户：":"AI： ")+
                            m.getContent())
                    .collect(Collectors.joining("\n"));

            SessionEmotionResponseDTO result=emotionClient.prompt()
                    .user(conversation)
                    .call()
                    .entity(SessionEmotionResponseDTO.class);

            sessionMapper.update(null, new LambdaUpdateWrapper<ConsultationSession>()
                    .eq(ConsultationSession::getId, sessionId)
                    .set(ConsultationSession::getLastEmotionAnalysis, JSONUtil.toJsonStr(result))
                    .set(ConsultationSession::getLastEmotionUpdatedAt, LocalDateTime.now()));
            log.info("会话{}情绪分析完成:{}",sessionId,result.getPrimaryEmotion());
        }catch(Exception e){
            log.warn("会话{}情绪分析失败，跳过本次",sessionId,e);
        }
    }
}
