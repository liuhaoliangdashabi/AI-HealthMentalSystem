package org.example.diaryservice.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.example.diaryservice.entity.EmotionDiary;
import org.example.diaryservice.util.RedisKeys;
import org.example.diaryservice.util.RocketMQUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 日记分析任务的消费者
 * 新建 group 用 latest 而不是 0：从 0 会把历史消息全部回放一遍，
 * 那些日记多半早就分析过了或者已经删了，白白刷一屏"日记不存在"。
 */
@Slf4j
@Service
@RocketMQMessageListener(
        topic="ai-task",
        selectorExpression = RocketMQUtil.TAG_DIARY,
        consumerGroup="diary-analysis-consumer",
        maxReconsumeTimes=3)
public class AiTaskConsumer implements RocketMQListener<String> {
    @Autowired
    private DiaryAnalyzer diaryAnalyzer;
    @Override
    public void onMessage(String message) {
        Long diaryId=Long.valueOf(message);
        if(!diaryAnalyzer.analyzeDiary(diaryId)){
            throw new IllegalStateException("日记分析失败，diaryId="+diaryId);
        }
    }
}
