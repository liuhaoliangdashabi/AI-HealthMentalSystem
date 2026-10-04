package org.example.aispringboot.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.example.aispringboot.DTO.command.*;
import org.example.aispringboot.DTO.response.*;
import org.example.aispringboot.common.Result;
import org.example.aispringboot.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api")
public class KnowledgeController {
    @Autowired
    private KnowledgeCategoryService categoryService;
    @Autowired
    private KnowledgeArticleService articleService;
    @Autowired
    private KnowledgeConsultationService consultationService;
    @Autowired
    private DiaryService diaryService;
    @Autowired
    private AnalyticsService analyticsService;
    @Autowired
    private FileService fileService;

    //--------------------------------category相关

    @GetMapping("/knowledge/category/tree")
    public Result<List<CategoryResponseDTO>> categoryTree(
            @AuthenticationPrincipal UserLoginResponseDTO.UserDetailResponseDTO user
    ){
        log.debug("Id={}查询分类树",user.getId());
        return Result.success(categoryService.listAll());
    }

    @DeleteMapping("/knowledge/category/{id}")
    public Result<Void> deleteCategory(@PathVariable Long id,
                                       @AuthenticationPrincipal UserLoginResponseDTO.UserDetailResponseDTO user){
        log.debug("Id={}删除分类：{}",user.getId(),id);
        categoryService.deleteCategory(id);
        return Result.success();
    }

    @DeleteMapping("/knowledge/category/{id}/{goToId}")
    public Result<Void> deleteCategory(@PathVariable Long id,
                                       @PathVariable Long goToId,
                                       @AuthenticationPrincipal UserLoginResponseDTO.UserDetailResponseDTO user){
        log.debug("Id={}删除分类{}，文章将挪到{}下",user.getId(),id,goToId);
        categoryService.deleteCategory(id,goToId);
        return Result.success();
    }

    //-----------------------------------Article相关
    @GetMapping("/knowledge/article/page")
    public Result<IPage<ArticleResponseDTO>> articlePage(
            @Valid ArticlePageQueryDTO pageQueryDTO){
        log.debug("分页查询文章列表");
        IPage<ArticleResponseDTO> articleList=articleService.selectPage(pageQueryDTO);
        return Result.success(articleList);
    }

    @PostMapping("/knowledge/article")
    public Result<Void> createArticle(
            @Valid @RequestBody ArticleCommandDTO commandDTO,
            @AuthenticationPrincipal UserLoginResponseDTO.UserDetailResponseDTO user){
        log.debug("Id={}新建文章",user.getId());
        String username=user.getUsername().isBlank()?
                user.getEmail():user.getUsername();
        articleService.createArticle(commandDTO,user.getId());
        return Result.success();
    }

    @PutMapping("/knowledge/article/{id}/status")
    public Result<Void> putArticleStatus(@PathVariable("id") String articleId,@Valid @RequestBody ArticleStatusCommandDTO status,
                                         @AuthenticationPrincipal UserLoginResponseDTO.UserDetailResponseDTO user){
        log.debug("Id={}修改文章状态",user.getId());
        articleService.putArticleStatus(articleId,status);
        return Result.success();
    }

    @GetMapping("/knowledge/article/{id}")
    public Result<ArticleResponseDTO> getArticleDetail(@PathVariable String id,
                                         @AuthenticationPrincipal UserLoginResponseDTO.UserDetailResponseDTO user){
        log.debug("Id={}获取文章articleId={}详情",user.getId(),id);
        ArticleResponseDTO responseDTO=articleService.getArticleDetail(id);
        return Result.success(responseDTO);
    }

    @PutMapping("/knowledge/article/{id}")
    public Result<Void> updateArticleDetail(@PathVariable String id,
                                      @Valid @RequestBody ArticleCommandDTO commandDTO,
                                      @AuthenticationPrincipal UserLoginResponseDTO.UserDetailResponseDTO user){
        log.debug("Id={}修改文章articleId={}详情",user.getId(),id);
        articleService.putArticleDetail(id,commandDTO);
        return Result.success();
    }
    @DeleteMapping("/knowledge/article/{id}")
    public Result<Void> deleteArticle(@PathVariable String id,
                                      @AuthenticationPrincipal UserLoginResponseDTO.UserDetailResponseDTO user){
        log.debug("Id={}要删除articleId={}",user.getId(),id);
        articleService.deleteArticle(id);
        return Result.success();
    }



    //-------------------------------------Consultation相关
    @GetMapping("/psychological-chat/sessions")
    public Result<IPage<ConsultationSessionResponseDTO>> getConsultationPage(@Valid ConsultationPageQueryDTO queryDTO){
        log.debug("查询心理会话列表，currentPage={}，size={}",queryDTO.getCurrentPage(),queryDTO.getSize());
        IPage<ConsultationSessionResponseDTO> responseDTO=consultationService.getConsultationPage(queryDTO);
        return Result.success(responseDTO);
    }

    @GetMapping("/psychological-chat/sessions/{sessionId}/messages")
    public Result<List<ConsultationMessageResponseDTO>> getConsultationDetail(@PathVariable Long sessionId,
                                                                              @AuthenticationPrincipal UserLoginResponseDTO.UserDetailResponseDTO user){
        log.debug("查询心理会话消息,sessionId={}",sessionId);
        List<ConsultationMessageResponseDTO> responseDTO=consultationService.getConsultationDetail(sessionId,user);
        return Result.success(responseDTO);
    }

    //-----------------------------------Emotion-diary相关
    @GetMapping("/emotion-diary/admin/page")
    public Result<IPage<DiaryResponseDTO>> getEmotionalPage( DiaryPageQueryDTO queryDTO){
        log.debug("查询情感日记列表，current={}，size={}",queryDTO.getCurrent(),queryDTO.getSize());
        IPage<DiaryResponseDTO> responseDTO=diaryService.getEmotionalPage(queryDTO);
        return Result.success(responseDTO);
    }

    @DeleteMapping("/emotion-diary/admin/{id}")
    public Result<Void> deleteEmotionDiary(@PathVariable("id")Long diaryId,
                                           @AuthenticationPrincipal UserLoginResponseDTO.UserDetailResponseDTO user){
        log.debug("Id={}要删除diaryId={}的日记",user.getId(),diaryId);
        diaryService.deleteEmotionDiary(diaryId);
        return Result.success();
    }

    //-------------------------------------File相关
    @PostMapping("/file/upload")
    public Result<Map.Entry<String,String>> uploadFile(@RequestParam("file") MultipartFile file,
                                        @RequestParam("businessType")String businessType,
                                        @RequestParam(value="businessId",required=false)String businessId,
                                        @RequestParam(value="businessField",required=false)String businessField,
                                        @AuthenticationPrincipal UserLoginResponseDTO.UserDetailResponseDTO user){
        log.debug("Id={}正在上传文件，businessType={},businessId={},businessField={}",
                user.getId(),businessType,businessId,businessField);
        String objectKey=fileService.upload(file,businessType,businessId,businessField, user.getId());
        return Result.success(Map.entry("filePath",objectKey));
    }

    //-----------------------------------Data-analytics相关
    @GetMapping("/data-analytics/overview")
    public Result<AnalyticsOverviewResponseDTO> analyticsOverview(
            @AuthenticationPrincipal UserLoginResponseDTO.UserDetailResponseDTO user){
        log.debug("Id={}查询数据分析总览",user.getId());
        return Result.success(analyticsService.getOverview());
    }
}
