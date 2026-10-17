package org.example.aispringboot.service;

import cn.hutool.crypto.digest.DigestUtil;
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
import org.springframework.util.DigestUtils;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
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
    private static final String UV_KEY="uv:";
    private static final Duration UV_TTL=Duration.ofDays(40);

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
        //一天一篇
        Long exists=emotionDiaryMapper.selectCount(new LambdaQueryWrapper<EmotionDiary>()
                .eq(EmotionDiary::getUserId,userId)
                .eq(EmotionDiary::getDiaryDate,commandDTO.getDiaryDate()));
        if(exists!=null && exists>0){
            throw new BusinessException("今天已经写过日记了，一天只能写一篇");
        }
        Boolean locked=null;
        String fp="";
        try {
            //写一篇日记5s，这也不算是什么人类了
            fp= DigestUtils.md5DigestAsHex(
                    (userId+"|"+commandDTO.getDiaryDate()+"|"+commandDTO.getDiaryContent())
                            .getBytes(StandardCharsets.UTF_8));
            locked=redis.opsForValue().setIfAbsent(DIARY_DUP_LOCK+fp,"1",Duration.ofSeconds(60));
        } catch (Exception e) {
            log.warn("用户提交日记加锁失败，Redis挂了，快去修复",e);
        }
        if(Boolean.FALSE.equals(locked)){
            log.info("日记重复提交，用户Id={}",userId);
            throw new BusinessException("请勿重复提交日记");
        }
        EmotionDiary diary=DiaryConvert.commandToEntity(commandDTO,userId);
        try {
            emotionDiaryMapper.insert(diary);
        } catch (Exception e) {
            releaseDupLock(userId,fp);
            throw e;//原样抛出就好
        }
        log.info("用户{}提交情绪日记成功，diaryId={},日期={}",userId,diary.getId(),diary.getDiaryDate());
        //pfcount出来是累计去重人数，曲线会一直往上走，只增不减
        //diaryDate来自前端，允许补写历史日记，按diary_date归桶
        String key=UV_KEY+diary.getDiaryDate();
        try{
            redis.opsForHyperLogLog().add(key,String.valueOf(userId));
            if (!Boolean.TRUE.equals(redis.expire(key,UV_TTL))) {
                log.warn("UV key设过期失败，key={}",key);
            }
        }catch(Exception e){
            log.warn("userId={}的uv记录失败，快去修复",userId,e);
        }
    }
    private void releaseDupLock(Long userId,String fp){
        try{
            redis.delete(DIARY_DUP_LOCK+fp);
        }catch(Exception e){
            log.warn("释放日记防重复锁失败，等TTL过期，userId={}",userId,e);
        }
    }
}
