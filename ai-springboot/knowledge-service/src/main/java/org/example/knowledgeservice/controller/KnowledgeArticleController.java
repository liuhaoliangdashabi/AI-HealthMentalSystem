package org.example.knowledgeservice.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.example.common.Result;
import org.example.knowledgeservice.DTO.command.ArticleCommandDTO;
import org.example.knowledgeservice.DTO.command.ArticlePageQueryDTO;
import org.example.knowledgeservice.DTO.command.ArticleStatusCommandDTO;
import org.example.knowledgeservice.DTO.response.ArticleResponseDTO;
import org.example.knowledgeservice.service.ArticleService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api")
public class KnowledgeArticleController {
    private final ArticleService articleService;
    public KnowledgeArticleController(ArticleService articleService){
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
            @RequestHeader(value="X-User-Id",required=false)Long userId,
            @RequestHeader(value="X-User-Name",required=false)String userName){
        log.debug("Id={}新建文章",userId);
//        String username=user.getUsername().isBlank()?
//                user.getEmail():user.getUsername();
        articleService.createArticle(commandDTO,userId,userName);
        return Result.success();
    }

    @PutMapping("/knowledge/article/{id}/status")
    public Result<Void> putArticleStatus(@PathVariable("id") String articleId,@Valid @RequestBody ArticleStatusCommandDTO status,
                                         @RequestHeader(value="X-User-Id",required=false)Long userId,
                                         @RequestHeader(value="X-User-Name",required=false)String userName){
        log.debug("Id={}修改文章状态",userId);
        articleService.putArticleStatus(articleId,status);
        return Result.success();
    }

    @PutMapping("/knowledge/article/{id}")
    public Result<Void> updateArticleDetail(@PathVariable String id,
                                            @Valid @RequestBody ArticleCommandDTO commandDTO,
                                            @RequestHeader(value="X-User-Id",required=false)Long userId){
        log.debug("Id={}修改文章articleId={}详情",userId,id);
        articleService.putArticleDetail(id,commandDTO);
        return Result.success();
    }
    @DeleteMapping("/knowledge/article/{id}")
    public Result<Void> deleteArticle(@PathVariable String id,
                                      @RequestHeader(value="X-User-Id",required=false)Long userId){
        log.debug("Id={}要删除articleId={}",userId,id);
        articleService.deleteArticle(id);
        return Result.success();
    }
}
