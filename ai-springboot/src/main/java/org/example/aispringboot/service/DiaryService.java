package org.example.aispringboot.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.example.aispringboot.DTO.command.DiaryPageQueryDTO;
import org.example.aispringboot.DTO.response.DiaryResponseDTO;
import org.example.aispringboot.entity.EmotionDiary;
import org.example.aispringboot.entity.User;
import org.example.aispringboot.exception.BusinessException;
import org.example.aispringboot.mapper.EmotionDiaryMapper;
import org.example.aispringboot.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigureOrder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
public class DiaryService {
    @Autowired
    private EmotionDiaryMapper emotionDiaryMapper;
    @Autowired
    private UserMapper userMapper;

    public IPage<DiaryResponseDTO> getEmotionalPage(DiaryPageQueryDTO query) {
        Page<EmotionDiary> page=new Page<>(query.getCurrent(),query.getSize());
        LambdaQueryWrapper<EmotionDiary> qw=new LambdaQueryWrapper<>();
        qw.eq(query.getUserId()!=null,EmotionDiary::getUserId, query.getUserId())
                .le(query.getMaxMoodScore()!=null, EmotionDiary::getMoodScore,query.getMaxMoodScore())
                .ge(query.getMinMoodScore()!=null, EmotionDiary::getMoodScore,query.getMinMoodScore())
                .like(StringUtils.hasText(query.getDominantEmotion()),
                        EmotionDiary::getDominantEmotion,query.getDominantEmotion());

        emotionDiaryMapper.selectPage(page,qw);
        Set<Long> userIds=page.getRecords().stream().map(EmotionDiary::getUserId).collect(Collectors.toSet());
        Map<Long,User> userMap=userIds.isEmpty()?Map.of():
                userMapper.selectBatchIds(userIds).stream()
                        .collect(Collectors.toMap(User::getId, user->user));
        userIds.removeAll(userMap.keySet());
        if(!userIds.isEmpty())log.warn("这些用户id不存在，可能是脏数据:{}",userIds);

        return page.convert(d-> {
            Long userId=d.getUserId();
            User u=userMap.get(userId);
            return DiaryResponseDTO.builder()
                    .id(d.getId())
                    .userId(d.getUserId())
                    .username(u==null?null:u.getUsername())
                    .nickname(u==null?null:u.getNickname())
                    .diaryDate(d.getDiaryDate())
                    .moodScore(d.getMoodScore())
                    .dominantEmotion(d.getDominantEmotion())
                    .emotionTriggers(d.getEmotionTriggers())
                    .diaryContent(d.getDiaryContent())
                    .sleepQuality(d.getSleepQuality())
                    .stressLevel(d.getStressLevel())
                    .aiEmotionAnalysis(d.getAiEmotionAnalysis())
                    .aiAnalysisUpdatedAt(d.getAiAnalysisUpdatedAt())
                    .createdAt(d.getCreatedAt())
                    .updatedAt(d.getUpdatedAt())
                    .build();
        });
    }

    public void deleteEmotionDiary(Long diaryId) {
        int count=emotionDiaryMapper.delete(new LambdaQueryWrapper<EmotionDiary>()
                .eq(EmotionDiary::getId,diaryId));
        if(count==0){
            log.warn("要删的数据不存在，diaryId={}",diaryId);
            throw new BusinessException("要删除的数据不存在");
        }
    }
}
