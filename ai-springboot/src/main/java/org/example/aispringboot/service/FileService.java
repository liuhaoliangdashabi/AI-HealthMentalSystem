package org.example.aispringboot.service;

import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.example.aispringboot.entity.SysFileInfo;
import org.example.aispringboot.mapper.FileMapper;
import org.example.aispringboot.util.OSSUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.time.LocalDateTime;
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
        String md5=null;
        try(InputStream in=file.getInputStream()){
            md5=DigestUtils.md5DigestAsHex(in);
        } catch (IOException e) {
            log.warn("计算文件md5失败，退化为普通上传",e);
        }
        String objectKey=null;
        if (md5!=null) {
            try{
                objectKey=redis.opsForValue().get(FILE_MD5_PREFIX+md5);
            }catch(Exception e){
                log.warn("获取文件缓存失败，正常上传,md5={}",md5,e);
            }
        }
        boolean cacheHit=objectKey!=null;
        if(cacheHit){
            log.info("秒传命中，md5={}，objectKey={}",md5,objectKey);
        }else{
            objectKey=ossUtil.upload(file,businessType.toLowerCase());
        }
        try {
            SysFileInfo info= SysFileInfo.builder()
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
                    .build();
            fileMapper.insert(info);
            if (cacheHit){
                log.info("秒传命中文件，文件上传成功，objectKey={},field={}",objectKey,info.getId());
            }else{
                log.info("秒传未命中文件，但文件上传成功，objectKey={},fileId={}",objectKey,info.getId());
            }
        } catch (Exception e) {
            if (cacheHit) {
                //不能补偿删除，文件可能被别的记录引用着，删了会把别人搞挂
                log.warn("秒传写库失败，放弃补偿，文件保留,md5={}",md5,e);
            }else{
                log.warn("写库失败，异步补偿，objectKey={}",objectKey,e);
                fileCompensator.compensateDelete(objectKey);
            }
        }

        //回填缓存
        if(!cacheHit && md5!=null){
            try{
                redis.opsForValue().set(FILE_MD5_PREFIX+md5,objectKey,FILE_MD5_TTL);
            }catch (Exception e){
                log.warn("文件上传成功，但写秒传缓存失败，md5={}",md5,e);
            }
        }
        return objectKey;
    }
}
