package org.example.diaryservice.service;

import lombok.extern.slf4j.Slf4j;
import org.example.diaryservice.util.RedisKeys;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * AI 任务队列的【生产者】。
 *
 * 只投递，不消费。Stream 是 stream:ai:task，眼 web 里那个同名 Stream
 * 靠 type 字段分流：DIARY_ANALYSIS / EMOTION_ANALYSIS。
 *
 * 消费者（AiTaskConsumer）暂时还留在 web，等 D8 收尾一起搬。
 * 搬完之后 web 那份必须停掉 —— 同 group 不同 consumerName 是"分摊"不是"重复"，
 * 两个实例同时跑会出现"一半日记结果来自新代码、一半来自旧代码"。
 */
@Slf4j
@Service
public class AiTaskQueueService {
    @Autowired
    private StringRedisTemplate redis;

    /**
     * 投递日记分析任务。
     * 两种任务共用一条 Stream 的原因：生命周期一样，都要 PEL 兜底、都要转死信，
     * 拆两条 Stream 就得维护两套兜底逻辑。
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
