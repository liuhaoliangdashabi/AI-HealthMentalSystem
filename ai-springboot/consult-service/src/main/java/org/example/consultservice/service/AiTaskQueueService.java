package org.example.consultservice.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.example.consultservice.util.RocketMQUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * AI 任务队列的【生产者】。
 *
 * 原来是 Redis Stream（XADD stream:ai:task），靠 type 字段和日记任务分流，
 * 消费者自己轮询 PEL 做重试和死信。现在换成 RocketMQ：
 * 重试、死信、堆积全由 broker 管，本模块只剩"投一条消息"这件事。
 *
 * topic 统一是 ai-task，靠 tag 分流：DIARY 归 diary-service，EMOTION 归本服务。
 * 两边 consumerGroup 不同，互不干扰。
 */
@Slf4j
@Service
public class AiTaskQueueService {
    @Autowired
    private RocketMQTemplate rocketMQTemplate;

    /**
     * 投递对话情绪分析任务。
     *
     * 调用点在 SSE 流的 doOnComplete 里 —— 那里抛任何异常都会污染已经推给前端的响应，
     * 所以这里必须自己吞掉：分析失败只是这一轮对话没有情绪标签，会话本身已经存好了。
     */
    public void enqueueEmotionAnalysis(Long dbSessionId){
        try{
            rocketMQTemplate.asyncSend("ai-task:" + RocketMQUtil.TAG_EMOTION,
                    String.valueOf(dbSessionId),
                    new SendCallback(){
                        @Override
                        public void onSuccess(SendResult sendResult) {
                            log.debug("情绪分析任务投递成功，msgId={}，sessionId={}",
                                    sendResult.getMsgId(), dbSessionId);
                        }

                        @Override
                        public void onException(Throwable e) {
                            log.error("情绪分析任务投递失败，会话{}这轮对话不会被分析", dbSessionId, e);
                        }
                    });
        }catch(Exception e){
            //name-server 连不上这类问题会在同步阶段就抛，走不到 SendCallback
            log.error("情绪分析任务投递异常，会话{}这轮对话不会被分析", dbSessionId, e);
        }
    }
}
