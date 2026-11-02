package org.example.aispringboot.service;

import lombok.extern.slf4j.Slf4j;
import org.example.aispringboot.util.RedisKeys;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;
@Slf4j
@Service
public class AiTaskQueueService {
    @Autowired
    private StringRedisTemplate redis;
    public void enqueueEmotionAnalysis(Long dbSessionId){
        try{
            redis.opsForStream().add(RedisKeys.AI_TASK_STREAM, Map.of(
                    "type","EMOTION_ANALYSIS",
                    "sessionId",String.valueOf(dbSessionId)
            ));
        }catch (Exception e){
            log.error("投递情绪分析任务失败，任务丢失！任务丢失！sessionId={}",dbSessionId,e);
        }

    }

    /**
     * 投递日记分析任务。和会话分析共用一条 Stream，靠 type 字段分流。
     * 两种任务的生命周期一样，都要 PEL 兜底、都要转死信，拆两条 Stream 就得维护两套兜底逻辑。
     * 老消息可能没有 type 字段，消费者要按 EMOTION_ANALYSIS 处理，否则历史积压全变毒消息。
     */
    public void enqueueDiaryAnalysis(Long diaryId){
        try{
            redis.opsForStream().add(RedisKeys.AI_TASK_STREAM, Map.of(
                    "type","DIARY_ANALYSIS",
                    "diaryId",String.valueOf(diaryId)
            ));
        }catch (Exception e){
            //日记已经落库了，分析只是锦上添花 —— 投递失败不能把用户的提交搞挂
            log.error("投递日记分析任务失败，日记{}不会自动分析",diaryId,e);
        }
    }
}
