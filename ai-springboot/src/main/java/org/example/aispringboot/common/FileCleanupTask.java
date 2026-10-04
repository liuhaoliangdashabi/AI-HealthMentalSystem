package org.example.aispringboot.common;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.example.aispringboot.entity.SysFileInfo;
import org.example.aispringboot.mapper.FileMapper;
import org.example.aispringboot.util.OSSUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
public class FileCleanupTask {
    @Autowired
    private FileMapper fileMapper;
    @Autowired
    private OSSUtil ossUtil;

    @Scheduled(cron="0 */20 * * * ?")
    public void cleanupOrphans(){
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
                log.warn("对账清除失败，下次再试");
            }
        }
    }
}
