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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import javax.print.DocFlavor;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
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
    @Autowired
    private StringRedisTemplate redis;

    private static final String AI_EMOTION_ANALYSIS_KEY="ai:emotion:analysis:";
    private static final Duration TTL=Duration.ofHours(1);

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

            //观前提醒：不是用来加速聊天的，防止同一段对话被重复计算
            String md5= DigestUtils.md5DigestAsHex(conversation.getBytes(StandardCharsets.UTF_8));
            String key=AI_EMOTION_ANALYSIS_KEY+md5;
            SessionEmotionResponseDTO result=null;

            try{
                String json=redis.opsForValue().get(key);
                if(json!=null)result=JSONUtil.toBean(json, SessionEmotionResponseDTO.class);
            }catch(Exception e){
                log.warn("AI情绪分析读缓存失败",e);
            }
            boolean cacheHit=result!=null;
            if(!cacheHit){
                result=emotionClient.prompt()
                        .user(conversation)
                        .call()
                        .entity(SessionEmotionResponseDTO.class);
            }

            if(result==null){
                log.warn("没读中缓存也没得到模型答复，不写库，也不写缓存，直接返回");
                return;
            }

            sessionMapper.update(null, new LambdaUpdateWrapper<ConsultationSession>()
                    .eq(ConsultationSession::getId, sessionId)
                    .set(ConsultationSession::getLastEmotionAnalysis, JSONUtil.toJsonStr(result))
                    .set(ConsultationSession::getLastEmotionUpdatedAt, LocalDateTime.now()));
            log.info("会话{}情绪分析完成:{}",sessionId,result.getPrimaryEmotion());

            if(!cacheHit && md5!=null){
                try{
                    redis.opsForValue().set(key, JSONUtil.toJsonStr(result), TTL);
                }catch(Exception e){
                    log.warn("情绪分析回填缓存失败",e);
                }
            }
        }catch(Exception e){
            log.warn("会话{}情绪分析失败，跳过本次",sessionId,e);
        }
    }
}
