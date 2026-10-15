package org.example.aispringboot.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.example.aispringboot.DTO.command.DiaryCommandDTO;
import org.example.aispringboot.DTO.command.DiaryPageQueryDTO;
import org.example.aispringboot.DTO.response.DiaryResponseDTO;
import org.example.aispringboot.entity.EmotionDiary;
import org.example.aispringboot.entity.User;
import org.example.aispringboot.exception.BusinessException;
import org.example.aispringboot.mapper.EmotionDiaryMapper;
import org.example.aispringboot.mapper.UserMapper;
import org.example.aispringboot.service.convert.DiaryConvert;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigureOrder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
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
    @Autowired
    private StringRedisTemplate redis;

    private static final String DIARY_DUP_LOCK="diary:dup:";
    private static final String IP_LIMIT_KEY="rl:ip:";

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
            User u=userMap.get(d.getUserId());
            return DiaryConvert.toResponse(d,u==null?null:u.getUsername(),u==null?null:u.getNickname());
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

    //C端提交日记。userId 从 token 来，不让前端传 —— 防止给别人写日记
    public void addEmotionDiary(DiaryCommandDTO commandDTO, Long userId) {
        Boolean locked=null;
        try {
            //写一篇日记15s，这也不算是什么人类了
            locked=redis.opsForValue().setIfAbsent(DIARY_DUP_LOCK+userId,"1", Duration.ofSeconds(15));
        } catch (Exception e) {
            log.warn("用户提交日记加锁失败，Redis挂了，快去修复",e);
        }
        if(Boolean.FALSE.equals(locked)){
            log.info("日记重复提交，用户Id={}",userId);
            try{
                redis.expire(DIARY_DUP_LOCK+userId,Duration.ofSeconds(5));
            }catch(Exception e){
                log.warn("用户提交日记检锁失败，快去修复",e);
            }
            throw new BusinessException("请勿重复提交日记");
        }
        EmotionDiary diary=DiaryConvert.commandToEntity(commandDTO,userId);
        emotionDiaryMapper.insert(diary);
        log.info("用户{}提交情绪日记成功，diaryId={},日期={}",userId,diary.getId(),diary.getDiaryDate());
    }
}
