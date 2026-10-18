package org.example.aispringboot.common;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.example.aispringboot.entity.SysFileInfo;
import org.example.aispringboot.mapper.FileMapper;
import org.example.aispringboot.util.OSSUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
public class FileCleanupTask {
    @Autowired
    private FileMapper fileMapper;
    @Autowired
    private OSSUtil ossUtil;
    @Autowired
    private StringRedisTemplate redis;

    private static final String LOCK_KEY="lock:fileCleanup";
    //调度间隔 20 分钟（cron 0 */20 * * * ?），锁的作用只是防多实例并发
    //TTL 必须大于单轮最坏耗时：否则任务还在删 OSS，锁先过期，另一个实例抢到锁就会重复清理同一批
    //若将来孤儿文件可能上万、单轮耗时接近 15 分钟，就要改成续期（watchdog）或分批限流
    private static final Duration LOCK_TTL=Duration.ofMinutes(15);
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT =
            new DefaultRedisScript<>(
                    "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
                    Long.class);

    @Scheduled(cron="0 */20 * * * ?")
    public void cleanupOrphans(){
        String randomToken= UUID.randomUUID().toString();
        Boolean locked;
        try {
            locked = redis.opsForValue().setIfAbsent(LOCK_KEY, randomToken,LOCK_TTL);
        } catch (Exception e) {
            log.warn("对账sys_file_info抢锁异常，本轮跳过",e);
            return;
        }
        if(!Boolean.TRUE.equals(locked)){//null多半为redis异常，注意三态判断
            log.info("抢锁失败，其他实例正在清理，跳过本次执行");
            return;
        }

        try {
            LambdaQueryWrapper<SysFileInfo> qw=new LambdaQueryWrapper<>();
            qw.eq(SysFileInfo::getIsTemp,1)
                    .lt(SysFileInfo::getCreateTime, LocalDateTime.now().minusMinutes(10));
            List<SysFileInfo> orphans=fileMapper.selectList(qw);
            if(orphans.isEmpty())return;
            log.info("对账sys_file_info，发现{}个待清理文件",orphans.size());

            for(SysFileInfo o:orphans){
                try{
                    ossUtil.delete(o.getFilePath());
                    fileMapper.deleteById(o.getId());
                }catch(Exception e){
                    log.warn("对账清除失败，下次再试",e);
                }
            }
        }finally{
            try{
                redis.execute(UNLOCK_SCRIPT,List.of(LOCK_KEY),randomToken);
            }catch(Exception e){
                log.warn("对账sys_file_info释放锁失败，等TTL自动过期",e);
            }
        }
    }
}
