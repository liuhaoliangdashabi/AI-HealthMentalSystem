package org.example.diaryservice.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.example.common.BusinessException;
import org.example.common.ResultCode;
import org.example.diaryservice.DTO.command.DiaryCommandDTO;
import org.example.diaryservice.DTO.command.DiaryPageQueryDTO;
import org.example.diaryservice.DTO.response.DiaryResponseDTO;
import org.example.diaryservice.entity.EmotionDiary;
import org.example.diaryservice.mapper.DiaryMapper;
import org.example.diaryservice.service.convert.DiaryConvert;
import org.example.diaryservice.util.RedisKeys;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class DiaryService {
    @Autowired
    private DiaryMapper emotionDiaryMapper;
//    @Autowired
//    private UserMapper userMapper;
    @Autowired
    private StringRedisTemplate redis;
    @Autowired
    private AiTaskQueueService queueService;

    private static final String DIARY_DUP_LOCK="diary:dup:";
    private static final String DIARY_USER_RATE_KEY ="diary:user:";
    private static final String DIARY_USER_EDIT_KEY="diary:user:edit:";

    public IPage<DiaryResponseDTO> getEmotionalPage(DiaryPageQueryDTO query,Long userId,
                                                    boolean isAdmin,String username) {
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
        return page.convert(d-> {
            return DiaryConvert.toResponse(d,username,username);
        });
    }

    /**
     * 删除日记。先查再删，因为 delete 的返回值分不清三件事：
     * ① 记录真的不存在（前端传了错的 id，是 bug，error 级）
     * ② 记录存在但不是你的（越权尝试，属安全事件，warn 级，不惊动运维）
     * ③ 查到之后被别人删了（并发，error 级）
     */
    public void deleteEmotionDiary(Long diaryId,Long userId,boolean isAdmin) {
        EmotionDiary diary=emotionDiaryMapper.selectById(diaryId);
        if(diary==null){
            log.error("删除失败：日记不存在，diaryId={}，操作者userId={}，isAdmin={}",diaryId,userId,isAdmin);
            throw new BusinessException("日记不存在");
        }
        //越权判断：userId 可能是 null（绕过网关直连），Objects.equals 不会 NPE
        if(!isAdmin && !Objects.equals(diary.getUserId(),userId)){
            log.warn("越权删除被拦截：diaryId={} 属于userId={}，操作者userId={}，isAdmin={}",
                    diaryId,diary.getUserId(),userId,isAdmin);
            throw new BusinessException(ResultCode.AUTHORIZED_ERROR.getCode(),"无权操作他人的日记",null);
        }
        if(emotionDiaryMapper.deleteById(diaryId)==0){
            log.error("删除失败：查询到但删除影响 0 行，疑似并发删除，diaryId={}，操作者userId={}",diaryId,userId);
            throw new BusinessException("日记不存在");
        }
    }

    //C端提交日记。userId 从 token 来，不让前端传 —— 防止给别人写日记
    public void addEmotionDiary(DiaryCommandDTO commandDTO, Long userId) {
        if(commandDTO.getDiaryDate().isBefore(LocalDate.now().minusDays(30))){
            throw new BusinessException("只能补写最近 30 天的日记");
        }
        //一天一篇
        try {
            String rateKey= DIARY_USER_RATE_KEY +userId;
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
        EmotionDiary diary= DiaryConvert.commandToEntity(commandDTO,userId);
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

        //投递异步分析任务。注意顺序：先落库、再投递 —— 反过来的话消费者可能比
        //insert 还快，拿着一个还不存在的 diaryId 去查，查到 null 就白跑一趟。
        queueService.enqueueDiaryAnalysis(diary.getId());
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
                //正文变了，旧的分析结果就过期了，必须清掉
                //  不清的话用户改完日记，详情里还是改之前那套情绪结论 —— 比"没有"更误导人。
                //  AI 分析结果是"输入的函数"，输入变了输出就得作废，这叫「派生数据失效」。
                .set(EmotionDiary::getAiEmotionAnalysis,null)
                .set(EmotionDiary::getAiAnalysisUpdatedAt,null)
                .set(EmotionDiary::getUpdatedAt, LocalDateTime.now());

        if(emotionDiaryMapper.update(null,uw)==0){
            throw new BusinessException("要修改的数据不存在");
        }

        //清空之后重新排队，等消费者算完再写回来
        queueService.enqueueDiaryAnalysis(diaryId);
    }

    /** 手动触发或补算一篇日记的 AI 分析。自动分析只覆盖上线后新写的日记，老日记靠这里补账。幂等：已有结果就跳过。 */
    public void requestDiaryAnalysis(Long diaryId, Long userId, boolean isAdmin){
        EmotionDiary diary=emotionDiaryMapper.selectById(diaryId);
        if(diary==null){
            throw new BusinessException("日记不存在");
        }
        //越权判断：C 端只能分析自己的，管理端可以帮别人补算
        //userId 可能是 null（绕过网关直连），Objects.equals 不会 NPE
        if(!isAdmin && !Objects.equals(diary.getUserId(), userId)){
            log.warn("越权分析被拦截：日记{}属于userId={}，操作者userId={}，isAdmin={}",
                    diaryId,diary.getUserId(),userId,isAdmin);
            throw new BusinessException(ResultCode.AUTHORIZED_ERROR.getCode(),"无权操作他人的日记",null);
        }
        if(diary.getAiEmotionAnalysis()!=null && !diary.getAiEmotionAnalysis().isBlank()){
            log.debug("日记{}已有分析结果，跳过重复投递",diaryId);
            return;
        }
        //防重复投递：前端会在打开详情时自动触发，多个管理员同时点开同一篇就是 N 次投递，
        //N 次 AI 调用。60 秒内只允许一次。
        String lockKey="diary:analyze:lock:"+diaryId;
        try{
            Boolean locked=redis.opsForValue().setIfAbsent(lockKey,"1",Duration.ofSeconds(60));
            if(!Boolean.TRUE.equals(locked)){
                log.debug("日记{}刚投递过分析任务，跳过",diaryId);
                return;
            }
        }catch(Exception e){
            //Redis 挂了就别拦了，宁可多算一次也别不算
            log.warn("日记{}分析防重锁失败，继续投递",diaryId,e);
        }
        queueService.enqueueDiaryAnalysis(diaryId);
    }

    private void releaseDupLock(Long userId,String fp){
        try{
            redis.delete(DIARY_DUP_LOCK+fp);
        }catch(Exception e){
            log.warn("释放日记防重复锁失败，等TTL过期，userId={}",userId,e);
        }
    }

//    /** 个人主页贡献日历：最近 days 天每天一条记录。只 select 三列，日历只要颜色，正文不用查。 */
//    public List<UserActivityItem> listRecentActivity(Long userId, int days){
//        if(userId==null)return List.of();
//        int span=Math.max(1,days);
//        LocalDate from=LocalDate.now().minusDays(span-1L);
//
//        List<EmotionDiary> list=emotionDiaryMapper.selectList(
//                new LambdaQueryWrapper<EmotionDiary>()
//                        .select(EmotionDiary::getDiaryDate,
//                                EmotionDiary::getMoodScore,
//                                EmotionDiary::getDominantEmotion)
//                        .eq(EmotionDiary::getUserId,userId)
//                        .ge(EmotionDiary::getDiaryDate,from)
//                        .orderByAsc(EmotionDiary::getDiaryDate)
//                        .orderByAsc(EmotionDiary::getId));
//
//        //同一天可能有多篇（库里没强唯一约束），后写的覆盖前面的，跟"最近一篇算数"一致
//        Map<LocalDate,UserActivityItem> byDate=new LinkedHashMap<>();
//        for(EmotionDiary d:list){
//            byDate.put(d.getDiaryDate(),UserActivityItem.builder()
//                    .diaryDate(d.getDiaryDate())
//                    .moodScore(d.getMoodScore())
//                    .dominantEmotion(d.getDominantEmotion())
//                    .build());
//        }
//        return new ArrayList<>(byDate.values());
//    }

}
