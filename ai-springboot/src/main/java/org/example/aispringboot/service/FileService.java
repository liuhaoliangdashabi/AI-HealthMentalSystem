package org.example.aispringboot.service;

import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.example.aispringboot.DTO.command.FileCommandDTO;
import org.example.aispringboot.entity.SysFileInfo;
import org.example.aispringboot.mapper.FileMapper;
import org.example.aispringboot.util.OSSUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
@Slf4j
@Service
public class FileService {
    private final FileMapper fileMapper;
    private final OSSUtil ossUtil;
    private final FileCompensator fileCompensator;
    public FileService(OSSUtil ossUtil,FileMapper fileMapper,FileCompensator fileCompensator){
        this.ossUtil=ossUtil;
        this.fileCompensator=fileCompensator;
        this.fileMapper=fileMapper;
    }

    public String upload(MultipartFile file,String businessType,String businessId,
                         String businessField, Long userId) {
        String objectKey= ossUtil.upload(file,businessType.toLowerCase());
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
            log.info("文件上传成功，objectKey={},fileId={}",objectKey,info.getId());
        } catch (Exception e) {
            log.warn("写库失败，异步补偿，objectKey={}",objectKey,e);
            fileCompensator.compensateDelete(objectKey);
        }
        return objectKey;
    }
}
