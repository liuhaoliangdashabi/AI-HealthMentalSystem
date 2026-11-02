package org.example.diaryservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.example.diaryservice.DTO.response.DiaryDailyRow;
import org.example.diaryservice.entity.EmotionDiary;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface DiaryMapper extends BaseMapper<EmotionDiary> {
    @Select("select round(avg(mood_score),1) from emotion_diary")
    Double avgMoodAll();

    @Select("select diary_date as diaryDate,count(*) as cnt,round(avg(mood_score),1) as avgMood"+
            " from emotion_diary where diary_date>=#{since} group by diary_date")
    List<DiaryDailyRow> dailySince(@Param("since")LocalDate since);
}
