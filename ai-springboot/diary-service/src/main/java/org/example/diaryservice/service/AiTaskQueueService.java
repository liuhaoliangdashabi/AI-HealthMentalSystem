package org.example.diaryservice.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.example.diaryservice.util.RedisKeys;
import org.example.diaryservice.util.RocketMQUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
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
    private RocketMQTemplate rocketMQTemplate;

    /**
     * 投递日记分析任务。
     * 两种任务共用一条 Stream 的原因：生命周期一样，都要 PEL 兜底、都要转死信，
     * 拆两条 Stream 就得维护两套兜底逻辑。
     */
    public void enqueueDiaryAnalysis(Long diaryId){
        rocketMQTemplate.asyncSend("ai-task:"+ RocketMQUtil.TAG_DIARY,String.valueOf(diaryId), new SendCallback(){

                    @Override
                    public void onSuccess(SendResult sendResult) {
                        log.debug("投递成功，msgId={}",sendResult.getMsgId());
                    }

                    @Override
                    public void onException(Throwable e) {
                        log.error("投递失败，日记已落库但AI任务没排上",e);
                    }
                });
    }
}
