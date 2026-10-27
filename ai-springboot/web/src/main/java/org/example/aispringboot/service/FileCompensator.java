package org.example.aispringboot.service;

import lombok.extern.slf4j.Slf4j;
import org.example.common.BusinessException;
import org.example.aispringboot.util.OSSUtil;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class FileCompensator {
    private final OSSUtil ossUtil;
    public FileCompensator(OSSUtil ossUtil){
        this.ossUtil=ossUtil;
    }

    //异步补偿——让他自己玩
    @Async
    public void compensateDelete(String objectKey){
        for (int i = 0; i <= 3; i++) {
            try{
                ossUtil.delete(objectKey);
                log.info("补偿删除成功，第{}次尝试,objectKey={}，即将退出",i,objectKey);
                return;
            }catch(Exception e){
                log.warn("补偿删除失败，第{}次尝试，objectKey={}",i,objectKey,e);
            }
        }
        //三次不行，不要再试，报告上级
        log.warn("三次补偿删除失败，不再尝试，OSS上残留孤儿文件：objectKey={}",objectKey);
        throw new BusinessException("三次补偿删除失败，需要人工干预");
    }
}
