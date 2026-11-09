package org.example.knowledgeservice.controller;

import lombok.extern.slf4j.Slf4j;
import org.example.knowledgeservice.enumClass.ArticleStatus;
import org.example.knowledgeservice.service.ArticleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/internal/knowledge")
public class InternalKnowledgeCascadeController {
    @Autowired
    private ArticleService articleService;

    @DeleteMapping("/by-user/{userId}/{targetId}")
    public int deleteById(@PathVariable("userId")Long userId,
                          @PathVariable("targetId")Long targetId){
        log.debug("user-serviceId={}要删除Id={}的文章",userId,targetId);
        return articleService.deleteById(targetId);
    }
}
