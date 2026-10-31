package org.example.diaryservice.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.example.common.Result;
import org.example.common.annotation.RequireIdentity;
import org.example.common.util.IdentityUtil;
import org.example.diaryservice.DTO.command.DiaryCommandDTO;
import org.example.diaryservice.DTO.command.DiaryPageQueryDTO;
import org.example.diaryservice.DTO.response.DiaryResponseDTO;
import org.example.diaryservice.service.DiaryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 身份校验统一由 common 的 IdentityInterceptor 做，方法上标 @RequireIdentity 即可。
 * 这里不再手写 checkIdentity，省得每加一个接口就抄一遍。
 */
@Slf4j
@RestController
@RequestMapping("/api")
public class DiaryController {
    @Autowired
    private DiaryService diaryService;

    @RequireIdentity
    @GetMapping("/emotion-diary/page")
    public Result<IPage<DiaryResponseDTO>> getEmotionalPage(DiaryPageQueryDTO queryDTO,
                                                            @RequestHeader(value="X-User-Id",required=false)Long userId,
                                                            @RequestHeader(value="X-User-Role",required=false)Integer roleType,
                                                            @RequestHeader(value="X-User-Name",required=false)String username){
        log.debug("查询情感日记列表，current={}，size={}",queryDTO.getCurrent(),queryDTO.getSize());
        IPage<DiaryResponseDTO> responseDTO=diaryService.getEmotionalPage(
                queryDTO,userId, IdentityUtil.isAdmin(roleType),username);
        return Result.success(responseDTO);
    }

    @RequireIdentity
    @DeleteMapping("/emotion-diary/{id}")
    public Result<Void> deleteEmotionDiary(@PathVariable("id")Long diaryId,
                                           @RequestHeader(value="X-User-Id",required=false)Long userId,
                                           @RequestHeader(value="X-User-Role",required=false)Integer roleType){
        log.debug("Id={}要删除diaryId={}的日记",userId,diaryId);
        diaryService.deleteEmotionDiary(diaryId, userId, IdentityUtil.isAdmin(roleType));
        return Result.success();
    }

    /** C端提交日记。userId 只从网关注入的头取，不接受前端传 —— 否则能替别人写日记 */
    @RequireIdentity
    @PostMapping("/emotion-diary")
    public Result<Void> addEmotionDiary(@Valid @RequestBody DiaryCommandDTO commandDTO,
                                        @RequestHeader(value="X-User-Id",required=false)Long userId){
        log.debug("Id={}提交情绪日记，日期={}",userId,commandDTO.getDiaryDate());
        diaryService.addEmotionDiary(commandDTO,userId);
        return Result.success();
    }

    /** 修改日记。归属校验在 service 的 update wrapper 里（eq userId），改别人的会 0 行 */
    @RequireIdentity
    @PutMapping("/emotion-diary/{id}")
    public Result<Void> updateEmotionDiary(@PathVariable("id")Long diaryId,
                                           @Valid @RequestBody DiaryCommandDTO commandDTO,
                                           @RequestHeader(value="X-User-Id",required=false)Long userId){
        log.debug("Id={}修改diaryId={}",userId,diaryId);
        diaryService.updateEmotionDiary(commandDTO,diaryId,userId);
        return Result.success();
    }

    /** 手动触发 AI 分析。前端打开详情时会自动调，重复调是幂等的（已有结果直接跳过） */
    @RequireIdentity
    @PostMapping("/emotion-diary/{id}/analyze")
    public Result<Void> analyzeEmotionDiary(@PathVariable("id")Long diaryId,
                                            @RequestHeader(value="X-User-Id",required=false)Long userId,
                                            @RequestHeader(value="X-User-Role",required=false)Integer roleType){
        log.debug("Id={}触发diaryId={}的AI分析",userId,diaryId);
        diaryService.requestDiaryAnalysis(diaryId,userId,IdentityUtil.isAdmin(roleType));
        return Result.success();
    }
}
