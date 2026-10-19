package org.example.aispringboot.AiService;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.extern.slf4j.Slf4j;
import org.example.aispringboot.DTO.response.SessionEmotionResponseDTO;
import org.example.aispringboot.entity.ConsultationMessage;
import org.example.aispringboot.entity.ConsultationSession;
import org.example.aispringboot.entity.EmotionDiary;
import org.example.aispringboot.mapper.ConsultationMessageMapper;
import org.example.aispringboot.mapper.ConsultationSessionMapper;
import org.example.aispringboot.mapper.EmotionDiaryMapper;
import org.example.aispringboot.util.RedisKeys;
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
    private EmotionDiaryMapper diaryMapper;
    @Autowired
    private StringRedisTemplate redis;

    private static final Duration TTL=Duration.ofHours(1);

    public boolean analyze(Long sessionId){
        try{
            List<ConsultationMessage> messages = messageMapper.selectList(
                    new LambdaQueryWrapper<ConsultationMessage>()
                            .eq(ConsultationMessage::getSessionId, sessionId)
                            .orderByDesc(ConsultationMessage::getCreatedAt)
                            .orderByDesc(ConsultationMessage::getId)   // id 兜底，防止同一秒乱序
                            .last("limit 20"));
            if(messages.isEmpty())return true;//空的，不需要操作
            Collections.reverse(messages);

            String conversation=messages.stream()
                    .map(m->(m.getSenderType()!=null && m.getSenderType()==1? "用户：":"AI： ")+
                            m.getContent())
                    .collect(Collectors.joining("\n"));

            //观前提醒：不是用来加速聊天的，防止同一段对话被重复计算
            String md5= DigestUtils.md5DigestAsHex(conversation.getBytes(StandardCharsets.UTF_8));
            String key=RedisKeys.AI_EMOTION_CACHE_PREFIX+md5;
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
                return false;
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
            return false;
        }
        return true;//到这里基本就好了
    }

    /**
     * 分析一篇情绪日记，结果写回 emotion_diary.ai_emotion_analysis。
     * 不能复用 analyze(Long) 的缓存：那个 key 是 md5(整段对话原文)，日记详情拼不出对话串。
     * 结果存数据库而不是 Redis，因为要持久可追溯，用户半年后翻回来还得看到当时的分析。
     *
     * @return true 表示可以 ACK（成功，或这条任务本来就不该重试）；false 表示留给 PEL 兜底重投
     */
    public boolean analyzeDiary(Long diaryId){
        EmotionDiary diary=diaryMapper.selectById(diaryId);
        if(diary==null){
            log.warn("日记{}不存在，跳过分析",diaryId);
            return true;   //日记都删了，重投一万次也还是没有，直接签收
        }

        String text=buildDiaryPrompt(diary);
        if(text==null){
            log.debug("日记{}没有任何文字内容，跳过分析",diaryId);
            return true;
        }

        SessionEmotionResponseDTO result;
        try{
            result=emotionClient.prompt()
                    .user(text)
                    .call()
                    .entity(SessionEmotionResponseDTO.class);
        }catch(Exception e){
            //网络抖动、模型限流 → 瞬时失败，不签收，留给 PEL 重投
            log.warn("日记{}AI分析调用失败，留给兜底重试",diaryId,e);
            return false;
        }

        if(result==null){
            log.warn("日记{}AI没有返回结构化结果，留给兜底重试",diaryId);
            return false;
        }

        diaryMapper.update(null, new LambdaUpdateWrapper<EmotionDiary>()
                .eq(EmotionDiary::getId,diaryId)
                .set(EmotionDiary::getAiEmotionAnalysis, JSONUtil.toJsonStr(result))
                .set(EmotionDiary::getAiAnalysisUpdatedAt, LocalDateTime.now()));
        log.info("日记{}分析完成：{}（强度{}）",diaryId,result.getPrimaryEmotion(),result.getEmotionScore());
        return true;
    }

    /** 把日记拼成给模型的输入。自评的情绪分、睡眠、压力也一起喂进去，光看文字容易把反话判成正向。 */
    private String buildDiaryPrompt(EmotionDiary diary){
        StringBuilder sb=new StringBuilder();
        if(diary.getDiaryContent()!=null && !diary.getDiaryContent().isBlank()){
            sb.append("日记正文：").append(diary.getDiaryContent()).append("\n");
        }
        if(diary.getEmotionTriggers()!=null && !diary.getEmotionTriggers().isBlank()){
            sb.append("情绪触发因素：").append(diary.getEmotionTriggers()).append("\n");
        }
        if(diary.getMoodScore()!=null){
            sb.append("自评情绪分（1-10，越高越好）：").append(diary.getMoodScore()).append("\n");
        }
        if(diary.getSleepQuality()!=null){
            sb.append("睡眠质量（1-5）：").append(diary.getSleepQuality()).append("\n");
        }
        if(diary.getStressLevel()!=null){
            sb.append("压力水平（1-5）：").append(diary.getStressLevel()).append("\n");
        }
        return sb.length()==0?null:sb.toString();
    }
}
