package org.example.aispringboot.service;

import cn.hutool.crypto.digest.DigestUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
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
import org.example.aispringboot.util.RedisKeys;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigureOrder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
    private static final String DIARY_USER_QUERY_KEY="diary:user:";
    private static final String DIARY_USER_EDIT_KEY="diary:user:edit:";

    public IPage<DiaryResponseDTO> getEmotionalPage(DiaryPageQueryDTO query,
                                                    Long userId,
                                                    boolean isAdmin) {
        Page<EmotionDiary> page=new Page<>(query.getCurrent(),query.getSize());
        LambdaQueryWrapper<EmotionDiary> qw=new LambdaQueryWrapper<>();

        //isAdmin&&是个开关，决定B端要不要用前端的值，不决定C端能看到谁
        //所以需要下面的if(!isAdmin)
        qw.eq(isAdmin && query.getUserId()!=null,EmotionDiary::getUserId, query.getUserId())
                .le(query.getMaxMoodScore()!=null, EmotionDiary::getMoodScore,query.getMaxMoodScore())
                .ge(query.getMinMoodScore()!=null, EmotionDiary::getMoodScore,query.getMinMoodScore())
                .like(StringUtils.hasText(query.getDominantEmotion()),
                        EmotionDiary::getDominantEmotion,query.getDominantEmotion())
                .orderByDesc(EmotionDiary::getDiaryDate)
                .orderByDesc(EmotionDiary::getId);
        //C端强制只查自己：上面那个 eq 带 isAdmin 条件，C端不会生效，这里才是唯一约束
        if(!isAdmin){
            qw.eq(EmotionDiary::getUserId, userId);
        }
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

    public void deleteEmotionDiary(Long diaryId,Long userId,boolean isAdmin) {
        LambdaQueryWrapper<EmotionDiary> qw=new LambdaQueryWrapper<EmotionDiary>()
                .eq(EmotionDiary::getId,diaryId);
        if(!isAdmin){
            qw.eq(EmotionDiary::getUserId,userId);
        }
        if(emotionDiaryMapper.delete(qw)==0){
            log.warn("要删的数据不存在，diaryId={}",diaryId);
            throw new BusinessException("要删除的数据不存在");
        }
    }

    //C端提交日记。userId 从 token 来，不让前端传 —— 防止给别人写日记
    public void addEmotionDiary(DiaryCommandDTO commandDTO, Long userId) {
        if(commandDTO.getDiaryDate().isBefore(LocalDate.now().minusDays(30))){
            throw new BusinessException("只能补写最近 30 天的日记");
        }
        //一天一篇
        try {
            String rateKey=DIARY_USER_QUERY_KEY+userId;
            Long n=redis.opsForValue().increment(rateKey);
            if(n==null)throw new Exception("Redis读取用户提交日记次数失败");
            if(n==1){
                Boolean expireDiaryUserId=redis.expire(rateKey,Duration.ofMinutes(1));
                if(!Boolean.TRUE.equals(expireDiaryUserId))
                    log.warn("日记限流key设过期失败，key={}",rateKey);
            }
            if(n>5)throw new BusinessException("日记提交太频繁，请稍后再试");
        }catch(BusinessException e){
            throw e;
        }catch (Exception e) {
            log.warn("日记登记次数失败",e);
        }
        Long exists=emotionDiaryMapper.selectCount(new LambdaQueryWrapper<EmotionDiary>()
                .eq(EmotionDiary::getUserId,userId)
                .eq(EmotionDiary::getDiaryDate,commandDTO.getDiaryDate()));
        if(exists!=null && exists>0){
            throw new BusinessException("今天已经写过日记了，一天只能写一篇");
        };

        Boolean locked=null;
        String fp="";
        try {
            //内容指纹+60s，同一份内容重复到达被拦（但是如果改几个字就搞攻击该怎办？）
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
        String key= RedisKeys.uv(diary.getDiaryDate());
        try{
            redis.opsForHyperLogLog().add(key,String.valueOf(userId));
            if (!Boolean.TRUE.equals(redis.expire(key, RedisKeys.UV_TTL))) {
                log.warn("UV key设过期失败，key={}",key);
            }
        }catch(Exception e){
            log.warn("userId={}的uv记录失败，快去修复",userId,e);
        }
    }

    public void updateEmotionDiary(DiaryCommandDTO dto,
                                   Long diaryId, Long userId) {
        try {
            String rateKey=DIARY_USER_EDIT_KEY+userId;
            Long n=redis.opsForValue().increment(rateKey);
            if (n!=null) {
                if(n==1){
                    Boolean expireDiaryUserId=redis.expire(rateKey,Duration.ofMinutes(1));
                    if(!Boolean.TRUE.equals(expireDiaryUserId))
                        log.warn("日记修改限流key设过期失败，key={}",rateKey);
                }
                if(n>20)throw new BusinessException("日记修改太频繁，请稍后再试");
            }
        }catch(BusinessException e){
            throw e;
        }catch (Exception e) {
            log.warn("日记修改限流次数出错",e);
        }

        LambdaUpdateWrapper<EmotionDiary> uw=new LambdaUpdateWrapper<EmotionDiary>()
                .eq(EmotionDiary::getId,diaryId)
                .eq(EmotionDiary::getUserId,userId)//鉴权
                .set(EmotionDiary::getMoodScore,dto.getMoodScore())
                .set(EmotionDiary::getDiaryContent,dto.getDiaryContent())
                .set(EmotionDiary::getDominantEmotion,dto.getDominantEmotion())
                .set(EmotionDiary::getEmotionTriggers,dto.getEmotionTriggers())
                .set(EmotionDiary::getSleepQuality,dto.getSleepQuality())
                .set(EmotionDiary::getStressLevel,dto.getStressLevel())
                .set(EmotionDiary::getUpdatedAt, LocalDateTime.now());

        if(emotionDiaryMapper.update(null,uw)==0){
            throw new BusinessException("要修改的数据不存在");
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
