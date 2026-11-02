package org.example.fileservice.controller;

import lombok.extern.slf4j.Slf4j;
import org.example.common.Result;
import org.example.common.annotation.RequireIdentity;
import org.example.fileservice.service.FileService;
import org.example.fileservice.util.OSSUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/file")
public class FileController {
    @Autowired
    private FileService fileService;
    @Autowired
    private OSSUtil ossUtil;

    @RequireIdentity
    @PostMapping("/upload")
    public Result<Map<String,String>> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam("businessType") String businessType,
            @RequestParam("businessId") String businessId,
            @RequestParam(value = "businessField", required = false) String businessField,
            @RequestHeader("X-User-Id") Long userId){
        String key=fileService.upload(file,businessType,businessId,businessField,userId);
        Map<String,String> data=new HashMap<>();
        data.put("filePath",key);
        data.put("fileUrl",ossUtil.getUrl(key));
        return Result.success(data);
    }
}
