package org.example.diaryservice.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.example.diaryservice.DTO.response.DiaryStatDTO;
import org.example.diaryservice.DTO.response.DiaryDailyRow;
import org.example.diaryservice.entity.EmotionDiary;
import org.example.diaryservice.mapper.DiaryMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.HashMap;

@Service
public class DiaryStatService {
    @Autowired
    private DiaryMapper diaryMapper;

    public DiaryStatDTO overview(int days){
        LocalDate today=LocalDate.now();
        LocalDate since=today.minusDays(Math.max(1,days)-1L);

        DiaryStatDTO dto=new DiaryStatDTO();
        dto.setTotal(diaryMapper.selectCount(null));
        dto.setTodayNew(diaryMapper.selectCount(
                new LambdaQueryWrapper<EmotionDiary>()
                        .eq(EmotionDiary::getDiaryDate,today)));
        dto.setAvgMoodScore(diaryMapper.avgMoodAll());

        for(DiaryDailyRow row :diaryMapper.dailySince(since)){
            DiaryStatDTO.Daily d=new DiaryStatDTO.Daily();
            d.setCount(row.getCnt());
            d.setAvgMoodScore(row.getAvgMood());
            dto.getDaily().put(row.getDiaryDate().toString(),d);
        }
        return dto;
    }
}
