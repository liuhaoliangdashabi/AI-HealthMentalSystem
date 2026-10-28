package org.example.knowledgeservice.controller;

import lombok.extern.slf4j.Slf4j;
import org.example.common.Result;
import org.example.knowledgeservice.DTO.response.CategoryResponseDTO;
import org.example.knowledgeservice.service.CategoryService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api")
public class KnowledgeCategoryController {

    private final CategoryService categoryService;

    public KnowledgeCategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @GetMapping("/knowledge/category/tree")
    public Result<List<CategoryResponseDTO>> categoryTree(
            @RequestHeader(value = "X-User-Id", required = false) String userId) {
        log.debug("Id={}查询分类树", userId);
        return Result.success(categoryService.listAll());
    }

    @DeleteMapping("/knowledge/category/{id}")
    public Result<Void> deleteCategory(@PathVariable Long id,
                                       @RequestHeader(value = "X-User-Id", required = false) String userId) {
        log.debug("Id={}删除分类：{}", userId, id);
        categoryService.deleteCategory(id);
        return Result.success();
    }

    @DeleteMapping("/knowledge/category/{id}/{goToId}")
    public Result<Void> deleteCategory(@PathVariable Long id,
                                       @PathVariable Long goToId,
                                       @RequestHeader(value = "X-User-Id", required = false) String userId) {
        log.debug("Id={}删除分类{}，文章将挪到{}下", userId, id, goToId);
        categoryService.deleteCategory(id, goToId);
        return Result.success();
    }
}
