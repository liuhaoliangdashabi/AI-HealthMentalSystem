package org.example.knowledgeservice.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.example.common.Result;
import org.example.knowledgeservice.DTO.command.ArticlePageQueryDTO;
import org.example.knowledgeservice.DTO.response.ArticleResponseDTO;
import org.example.knowledgeservice.service.KnowledgeArticleService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api")
public class KnowledgeArticleController {
    private final KnowledgeArticleService articleService;
    public KnowledgeArticleController(KnowledgeArticleService articleService){
        this.articleService=articleService;
    }

    @GetMapping("/knowledge/article/page")
    public Result<IPage<ArticleResponseDTO>> articlePage(
            @Valid ArticlePageQueryDTO pageQueryDTO,
            @RequestHeader(value="X-User-Role",required=false) Integer roleType){
        log.debug("role={}分页查询文章列表",roleType);
        return Result.success(articleService.selectPage(pageQueryDTO,roleType));
    }

    @GetMapping("/knowledge/article/{id}")
    public Result<ArticleResponseDTO> getArticleDetail(
            @PathVariable String id,
            @RequestHeader(value="X-User-Role",required=false)Integer roleType){
        log.debug("role={}获取文章articleId={}详情",roleType,id);
        return Result.success(articleService.getArticleDetail(id,roleType));
    }

    @GetMapping("/knowledge/article/hot")
    public Result<List<ArticleResponseDTO>> getHotArticles(
            @RequestHeader(value="X-User-Role",required=false)Integer roleType){
        log.debug("role={}查询文章热度列表",roleType);
        return Result.success(articleService.getHotArticle());
    }

    //今天还没来得及改，明天继续
    @PostMapping("/knowledge/article")
    public Result<Void> createArticle(
            @Valid @RequestBody ArticleCommandDTO commandDTO,
            @AuthenticationPrincipal UserLoginResponseDTO.UserDetailResponseDTO user){
        log.debug("Id={}新建文章",user.getId());
//        String username=user.getUsername().isBlank()?
//                user.getEmail():user.getUsername();
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
}
