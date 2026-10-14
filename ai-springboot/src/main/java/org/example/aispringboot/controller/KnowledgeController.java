package org.example.aispringboot.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.example.aispringboot.DTO.command.*;
import org.example.aispringboot.DTO.response.*;
import org.example.aispringboot.common.Result;
import org.example.aispringboot.service.*;
import org.example.aispringboot.util.OSSUtil;
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
    private DiaryService diaryService;
    @Autowired
    private AnalyticsService analyticsService;
    @Autowired
    private FileService fileService;
    @Autowired
    private OSSUtil ossUtil;

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
            @Valid ArticlePageQueryDTO pageQueryDTO,
            @AuthenticationPrincipal UserLoginResponseDTO.UserDetailResponseDTO user){
        log.debug("Id={}分页查询文章列表",user.getId());
        IPage<ArticleResponseDTO> articleList=articleService.selectPage(pageQueryDTO,user);
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
        ArticleResponseDTO responseDTO=articleService.getArticleDetail(id,user);
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

    @GetMapping("/knowledge/article/hot")
    public Result<List<ArticleResponseDTO>> getHotArticles(@AuthenticationPrincipal UserLoginResponseDTO.UserDetailResponseDTO user){
        log.debug("Id={}查询文章热度列表",user.getId());
        List<ArticleResponseDTO> hotArticle=articleService.getHotArticle();
        return Result.success(hotArticle);
    }



    //-------------------------------------Consultation相关
    // 会话列表、会话消息已移到 PsycholigicalChatController

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
    //返回两个：filePath 是入库用的相对路径，fileUrl 是前端预览用的完整地址
    @PostMapping("/file/upload")
    public Result<Map<String,String>> uploadFile(@RequestParam("file") MultipartFile file,
                                        @RequestParam("businessType")String businessType,
                                        @RequestParam(value="businessId",required=false)String businessId,
                                        @RequestParam(value="businessField",required=false)String businessField,
                                        @AuthenticationPrincipal UserLoginResponseDTO.UserDetailResponseDTO user){
        log.debug("Id={}正在上传文件，businessType={},businessId={},businessField={}",
                user.getId(),businessType,businessId,businessField);
        String objectKey=fileService.upload(file,businessType,businessId,businessField, user.getId());
        return Result.success(Map.of(
                "filePath",objectKey,
                "fileUrl",ossUtil.getUrl(objectKey)));
    }

    //-----------------------------------Data-analytics相关
    @GetMapping("/data-analytics/overview")
    public Result<AnalyticsOverviewResponseDTO> analyticsOverview(
            @AuthenticationPrincipal UserLoginResponseDTO.UserDetailResponseDTO user){
        log.debug("Id={}查询数据分析总览",user.getId());
        return Result.success(analyticsService.getOverview());
    }
}
