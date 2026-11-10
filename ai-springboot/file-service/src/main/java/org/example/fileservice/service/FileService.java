package org.example.fileservice.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import lombok.extern.slf4j.Slf4j;

import org.example.common.BusinessException;
import org.example.fileservice.entity.SysFileInfo;
import org.example.fileservice.mapper.FileMapper;
import org.example.fileservice.util.OSSUtil;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Locale;

@Slf4j
@Service
public class FileService {
    private final FileMapper fileMapper;
    private final OSSUtil ossUtil;
    private final FileCompensator fileCompensator;
    private final StringRedisTemplate redis;

    private static final String FILE_MD5_PREFIX="file:md5:";
    private static final Duration FILE_MD5_TTL=Duration.ofDays(7);

    public FileService(OSSUtil ossUtil,FileMapper fileMapper,
                       FileCompensator fileCompensator,
                       StringRedisTemplate redis){
        this.ossUtil=ossUtil;
        this.fileCompensator=fileCompensator;
        this.fileMapper=fileMapper;
        this.redis=redis;
    }

    public String upload(MultipartFile file,String businessType,String businessId,
                         String businessField, Long userId) {
        String objectKey=ossUtil.upload(file,businessType.toLowerCase());
        try{
            fileMapper.insert(SysFileInfo.builder()
                    .originalName(file.getOriginalFilename())
                    .filePath(objectKey)
                    .fileSize(file.getSize())
                    .fileType("IMG")
                    .businessType(businessType)
                    .businessId(businessId)
                    .businessField(businessField)
                    .uploadUserId(userId)
                    .isTemp(0)
                    .status(SysFileInfo.STATUS_NORMAL)
                    .createTime(LocalDateTime.now())
                    .build());
        }catch(Exception e){
            log.error("写库失败，已触发异步补偿,objectKey={}",objectKey,e);
            fileCompensator.compensateDelete(objectKey);
            //不能返回这个 key：补偿已经把 OSS 文件删了，库里也没有记录，
            //调用方把它存进 user.avatar 就是一张永远 404 的图
            throw new BusinessException("文件上传失败，请重试");
        }
        return objectKey;
    }

    public int markTempByUser(Long userId){
        return fileMapper.update(null,new LambdaUpdateWrapper<SysFileInfo>()
                .eq(SysFileInfo::getUploadUserId,userId)
                .eq(SysFileInfo::getIsTemp,0)
                .set(SysFileInfo::getIsTemp,1));
    }

    public int deleteByObjectKey(String objectKey,Long userId){
        if(objectKey==null || objectKey.isBlank())return 0;
        //不按 upload_user_id 过滤：文章封面可能是管理员代传的，按作者 id 匹配会 0 行、静默不删。
        //秒传已关，一个 key 只对应一条记录，按 key 删是安全的；userId 只用于日志追溯。
        int rows=fileMapper.delete(new LambdaQueryWrapper<SysFileInfo>()
                .eq(SysFileInfo::getFilePath,objectKey));
        if(rows>0 && !ossUtil.delete(objectKey)){
            log.warn("记录已删但oss没删掉，需人工清理：{}",objectKey);
        }
        return rows;
    }
}
