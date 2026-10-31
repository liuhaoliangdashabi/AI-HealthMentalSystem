package org.example.diaryservice.service;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.extern.slf4j.Slf4j;
import org.example.aicommon.dto.SessionEmotionResponseDTO;
import org.example.diaryservice.entity.EmotionDiary;
import org.example.diaryservice.mapper.DiaryMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 日记的情绪分析。从 web 的 EmotionAnalysisService 里剥出来的「日记那一半」。
 *
 * 会话分析（analyze(Long sessionId)）留在 web —— 它要查 consultation_message /
 * consultation_session 两张表，那些表在咨询域，8030 不该碰。
 *
 * 返回值语义必须保持不变，消费者靠它决定 ACK 还是留给 PEL 重投：
 * true  = 可以签收（成功，或这条本来就不该重试）
 * false = 瞬时失败，留着重投
 */
@Slf4j
@Service
public class DiaryAnalyzer {

    @Autowired
    @Qualifier("emotion-analyzer")
    private ChatClient emotionClient;

    @Autowired
    private DiaryMapper diaryMapper;

    public boolean analyzeDiary(Long diaryId){
        EmotionDiary diary=diaryMapper.selectById(diaryId);
        if(diary==null){
            //注意：这里以前读的是 assistant 库，搬到 8030 后才真正读到日记本体的库。
            //如果还看到这条日志，说明库连错了或者日记确实被删了
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
