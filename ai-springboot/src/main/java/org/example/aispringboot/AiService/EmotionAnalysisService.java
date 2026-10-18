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

    //同一段对话只分析一次：重连、重试、并发触发都复用上次结果
    private static final String AI_EMOTION_CACHE_PREFIX="ai:emotion:";
    private static final Duration AI_EMOTION_CACHE_TTL=Duration.ofHours(24);

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

            //用对话内容的指纹做 key：内容没变就不重复打大模型
            String cacheKey=AI_EMOTION_CACHE_PREFIX+
                    DigestUtils.md5DigestAsHex(conversation.getBytes(StandardCharsets.UTF_8));
            SessionEmotionResponseDTO result=null;
            try{
                String cached=redis.opsForValue().get(cacheKey);
                if(cached!=null){
                    result=JSONUtil.toBean(cached,SessionEmotionResponseDTO.class);
                    log.info("会话{}情绪分析命中缓存，省一次大模型调用",sessionId);
                }
            }catch(Exception e){
                log.warn("读AI情绪缓存失败，回源大模型，sessionId={}",sessionId,e);
            }

            if(result==null){
                result=emotionClient.prompt()
                        .user(conversation)
                        .call()
                        .entity(SessionEmotionResponseDTO.class);
                if(result==null){
                    log.warn("会话{}情绪分析返回空，跳过本次入库",sessionId);
                    return;
                }
                try{
                    redis.opsForValue().set(cacheKey,JSONUtil.toJsonStr(result),AI_EMOTION_CACHE_TTL);
                }catch(Exception e){
                    log.warn("写AI情绪缓存失败，不影响本次结果，sessionId={}",sessionId,e);
                }
            }

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
