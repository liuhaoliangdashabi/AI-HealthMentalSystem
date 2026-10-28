package org.example.diaryservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.example.diaryservice.entity.EmotionDiary;

@Mapper
public interface DiaryMapper extends BaseMapper<EmotionDiary> {
}
