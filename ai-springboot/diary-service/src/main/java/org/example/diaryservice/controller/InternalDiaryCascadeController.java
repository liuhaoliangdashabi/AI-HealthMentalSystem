package org.example.diaryservice.controller;

import lombok.extern.slf4j.Slf4j;
import org.example.diaryservice.service.DiaryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/internal/diary")
public class InternalDiaryCascadeController {
    @Autowired
    private DiaryService diaryService;

    @DeleteMapping("/by-user/{userId}/{targetId}")
    public int deleteById(@PathVariable("userId")Long userId,
                          @PathVariable("targetId")Long targetId){
        log.debug("user-serviceId={}要删除Id={}的日记",userId,targetId);
        return diaryService.deleteById(targetId);
    }
}
