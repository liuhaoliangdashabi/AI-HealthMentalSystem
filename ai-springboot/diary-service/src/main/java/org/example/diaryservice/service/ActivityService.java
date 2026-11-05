package org.example.diaryservice.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.example.diaryservice.DTO.response.UserActivityItem;
import org.example.diaryservice.entity.EmotionDiary;
import org.example.diaryservice.mapper.DiaryMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
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

    /**
     * 个人主页贡献日历：只读三列、每次进主页都拉一遍，读多写少 → 缓存 10 分钟。
     * 写日记/改日记/删日记都会清掉（见 DiaryService 上的 @CacheEvict）。
     * 返回 ArrayList 而不是 List.of()：不可变集合写进 JSON 会带 JDK 内部类名，读回来反序列化失败。
     */
    @Cacheable(cacheNames = "activity", key = "#userId + ':' + #days")
    public List<UserActivityItem> listRecentActivity(Long userId, int days) {
        if (userId == null) return new ArrayList<>();
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
