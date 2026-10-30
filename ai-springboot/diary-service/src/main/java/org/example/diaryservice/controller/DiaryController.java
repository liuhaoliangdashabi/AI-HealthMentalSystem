package org.example.diaryservice.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.extern.slf4j.Slf4j;
import org.example.common.BusinessException;
import org.example.common.Result;
import org.example.common.ResultCode;
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
        checkIdentity(userId,roleType,"/emotion-diary/page");
        //常量在前：roleType 为 null 时返回 false，落到"非管理员"，不会 NPE
        boolean isAdmin=Integer.valueOf(2).equals(roleType);
        IPage<DiaryResponseDTO> responseDTO=diaryService.getEmotionalPage(queryDTO,userId,isAdmin,username);
        return Result.success(responseDTO);
    }

    @DeleteMapping("/emotion-diary/{id}")
    public Result<Void> deleteEmotionDiary(@PathVariable("id")Long diaryId,
                                           @RequestHeader(value="X-User-Id",required=false)Long userId,
                                           @RequestHeader(value="X-User-Role",required=false)Integer roleType){
        log.debug("Id={}要删除diaryId={}的日记",userId,diaryId);
        checkIdentity(userId,roleType,"/emotion-diary/{id}");
        diaryService.deleteEmotionDiary(diaryId, userId, Integer.valueOf(2).equals(roleType));
        return Result.success();
    }

    //身份缺失只可能是绕过了网关，属配置/调用方问题：warn，不报 error
    private void checkIdentity(Long userId,Integer roleType,String path){
        if(userId==null||roleType==null){
            log.warn("缺少用户身份请求头，拒绝访问：path={}，X-User-Id={}，X-User-Role={}",path,userId,roleType);
            throw new BusinessException(ResultCode.UNAUTHORIZED.getCode(),"缺少用户身份信息，请重新登录",null);
        }
    }
}
