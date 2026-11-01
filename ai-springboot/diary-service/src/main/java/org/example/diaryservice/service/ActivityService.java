package org.example.diaryservice.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.example.diaryservice.DTO.response.UserActivityItem;
import org.example.diaryservice.entity.EmotionDiary;
import org.example.diaryservice.mapper.DiaryMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 个人主页贡献日历。
 * <p>
 * 单独一个类而不是塞进 DiaryService：这块只读三列、只服务一个页面，
 * 和日记增删改没有共享状态，拆开反而好测。
 */
@Slf4j
@Service
public class ActivityService {

    @Autowired
    private DiaryMapper diaryMapper;

    /** 最近 days 天，每天一条。只 select 三列，日历只要颜色，正文不用查 */
    public List<UserActivityItem> listRecentActivity(Long userId, int days) {
        if (userId == null) return List.of();
        int span = Math.max(1, days);
        LocalDate from = LocalDate.now().minusDays(span - 1L);

        List<EmotionDiary> list = diaryMapper.selectList(
                new LambdaQueryWrapper<EmotionDiary>()
                        .select(EmotionDiary::getDiaryDate,
                                EmotionDiary::getMoodScore,
                                EmotionDiary::getDominantEmotion)
                        .eq(EmotionDiary::getUserId, userId)
                        .ge(EmotionDiary::getDiaryDate, from)
                        .orderByAsc(EmotionDiary::getDiaryDate)
                        .orderByAsc(EmotionDiary::getId));

        //同一天可能有多篇（库里没强唯一约束），后写的覆盖前面的，跟"最近一篇算数"一致
        Map<LocalDate, UserActivityItem> byDate = new LinkedHashMap<>();
        for (EmotionDiary d : list) {
            byDate.put(d.getDiaryDate(), UserActivityItem.builder()
                    .diaryDate(d.getDiaryDate())
                    .moodScore(d.getMoodScore())
                    .dominantEmotion(d.getDominantEmotion())
                    .build());
        }
        return new ArrayList<>(byDate.values());
    }
}
