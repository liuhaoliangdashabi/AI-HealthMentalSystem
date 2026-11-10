package org.example.fileservice.controller;

import lombok.extern.slf4j.Slf4j;
import org.example.fileservice.service.FileService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/internal/file")
public class InternalFileController {
    @Autowired
    private FileService fileService;

    @PostMapping("/by-key")
    public int deleteByKey(@RequestParam("objectKey")String objectKey,
                           @RequestParam("userId")Long userId){
        log.info("user{}换头像，旧头像清理，objectKey={}",userId,objectKey);
        return fileService.deleteByObjectKey(objectKey,userId);
    }

    @DeleteMapping("/by-user")
    public int markTempByUser(@RequestParam("userId")Long userId){
        log.info("user{}注销，名下文件全部打标等待清理",userId);
        return fileService.markTempByUser(userId);
    }
}
