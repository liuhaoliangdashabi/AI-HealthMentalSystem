package org.example.aispringboot.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.example.aispringboot.DTO.response.UserActivityItem;
import org.example.aispringboot.entity.EmotionDiary;
import org.example.aispringboot.mapper.EmotionDiaryMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 日记的增删改查在 D7 已整体迁到 diary-service（8030），这里只剩个人主页贡献日历。
 * 它属于 user 域，等 D11 拆 user-service 时一起搬走。
 * 注意：读的仍然是 mental_health_assistant 库的旧表，迁完 user-service 才算真正对齐。
 */
@Slf4j
@Service
public class DiaryService {
    @Autowired
    private EmotionDiaryMapper emotionDiaryMapper;

    /** 个人主页贡献日历：最近 days 天每天一条记录。只 select 三列，日历只要颜色，正文不用查。 */
    public List<UserActivityItem> listRecentActivity(Long userId, int days){
        if(userId==null)return List.of();
        int span=Math.max(1,days);
        LocalDate from=LocalDate.now().minusDays(span-1L);

        List<EmotionDiary> list=emotionDiaryMapper.selectList(
                new LambdaQueryWrapper<EmotionDiary>()
                        .select(EmotionDiary::getDiaryDate,
                                EmotionDiary::getMoodScore,
                                EmotionDiary::getDominantEmotion)
                        .eq(EmotionDiary::getUserId,userId)
                        .ge(EmotionDiary::getDiaryDate,from)
                        .orderByAsc(EmotionDiary::getDiaryDate)
                        .orderByAsc(EmotionDiary::getId));

        //同一天可能有多篇（库里没强唯一约束），后写的覆盖前面的，跟"最近一篇算数"一致
        Map<LocalDate,UserActivityItem> byDate=new LinkedHashMap<>();
        for(EmotionDiary d:list){
            byDate.put(d.getDiaryDate(),UserActivityItem.builder()
                    .diaryDate(d.getDiaryDate())
                    .moodScore(d.getMoodScore())
                    .dominantEmotion(d.getDominantEmotion())
                    .build());
        }
        return new ArrayList<>(byDate.values());
    }

}
