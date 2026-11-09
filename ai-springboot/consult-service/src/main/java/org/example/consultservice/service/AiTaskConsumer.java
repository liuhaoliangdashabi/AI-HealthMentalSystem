package org.example.consultservice.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.example.consultservice.util.RocketMQUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 对话情绪分析任务的消费者。替换掉原来 216 行的 Redis Stream PEL 轮询：
 * 那时要自己 MKSTREAM 建组、每 800ms XREAD、每 30 秒扫 PEL 认领超时消息、
 * 重试 3 次转死信、每小时 XTRIM 裁剪 —— 这些现在全是 broker 的事。
 *
 * 三个关键约定：
 * 1. selectorExpression 用常量，且必须和生产者拼的 tag 逐字一致（大小写敏感）。
 *    写错不报错，消息静默丢弃：offset 正常前进，onMessage 一次都不执行。
 * 2. group 名不和 diary 的重复。同 topic 不同 group 是"各收一份"，不是分摊。
 * 3. maxReconsumeTimes=3 之后进 %DLQ%，别再自己写死信逻辑。
 *
 * 幂等：analyze 内部按对话内容算 md5 查 Redis 缓存，同一段对话重投不会重复调模型。
 * 所以这里不像 diary 那样需要 SETNX 分布式锁。
 */
@Slf4j
@Service
@RocketMQMessageListener(
        topic="ai-task",
        selectorExpression = RocketMQUtil.TAG_EMOTION,
        consumerGroup="consult-analysis-consumer",
        maxReconsumeTimes=3)
public class AiTaskConsumer implements RocketMQListener<String> {

    @Autowired
    private EmotionAnalysisService analysisService;

    /**
     * 返回 false 必须抛异常，否则 MQ 认为消费成功、不会重投。
     *
     * analyze 内部把所有异常都 catch 了（模型限流、DB 抖动），失败只返回 false，
     * 所以"重试"这个动作要在这里主动触发：抛出去 → broker 重投 → 3 次后进死信。
     */
    @Override
    public void onMessage(String message) {
        Long sessionId = Long.valueOf(message);
        if(!analysisService.analyze(sessionId)){
            throw new IllegalStateException("情绪分析失败，sessionId=" + sessionId);
        }
    }
}
