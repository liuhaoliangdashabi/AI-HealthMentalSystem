package org.example.diaryservice.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.extern.slf4j.Slf4j;
import org.example.common.Result;
import org.example.diaryservice.DTO.command.DiaryPageQueryDTO;
import org.example.diaryservice.DTO.response.DiaryResponseDTO;
import org.example.diaryservice.service.DiaryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api")
public class DiaryController {
    @Autowired
    private DiaryService diaryService;
    @GetMapping("/emotion-diary/page")
    public Result<IPage<DiaryResponseDTO>> getEmotionalPage(DiaryPageQueryDTO queryDTO,
                                                            @RequestHeader(value="X-User-Id",required=false)Long userId,
                                                            @RequestHeader(value="X-User-Role",required=false)Integer roleType,
                                                            @RequestHeader(value="X-User-Name",required=false)String username){
        log.debug("查询情感日记列表，current={}，size={}",queryDTO.getCurrent(),queryDTO.getSize());
        IPage<DiaryResponseDTO> responseDTO=diaryService.getEmotionalPage(queryDTO,userId,roleType.equals(2),username);
        return Result.success(responseDTO);
    }

    @DeleteMapping("/emotion-diary/{id}")
    public Result<Void> deleteEmotionDiary(@PathVariable("id")Long diaryId,
                                           @RequestHeader(value="X-User-Id",required=false)Long userId,
                                           @RequestHeader(value="X-User-Role",required=false)Integer roleType){
        log.debug("Id={}要删除diaryId={}的日记",userId,diaryId);
        diaryService.deleteEmotionDiary(diaryId, userId, roleType.equals(2));
        return Result.success();
    }
}
