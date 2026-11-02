package org.example.diaryservice.controller;

import lombok.extern.slf4j.Slf4j;
import org.example.diaryservice.DTO.response.DiaryStatDTO;
import org.example.diaryservice.entity.EmotionDiary;
import org.example.diaryservice.mapper.DiaryMapper;
import org.example.diaryservice.service.DiaryStatService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.example.common.util.IdentityUtil.HEADER_USER_ID;

@Slf4j
@RestController
@RequestMapping("/internal/diary/stat")
public class InternalDiaryController{
    @Autowired
    private DiaryStatService diaryStatService;
    @GetMapping("/overview")
    public DiaryStatDTO overview(@RequestParam(defaultValue="30") int days){
        log.debug("Analytics部分发出请求，查询Diary部分数据");
        return diaryStatService.overview(days);
    }
}
