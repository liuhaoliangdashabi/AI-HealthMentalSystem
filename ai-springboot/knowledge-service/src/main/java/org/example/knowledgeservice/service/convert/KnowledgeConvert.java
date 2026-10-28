package org.example.knowledgeservice.service.convert;



import org.example.knowledgeservice.DTO.command.ArticleCommandDTO;
import org.example.knowledgeservice.DTO.response.ArticleResponseDTO;
import org.example.knowledgeservice.DTO.response.CategoryResponseDTO;
import org.example.knowledgeservice.entity.KnowledgeArticle;
import org.example.knowledgeservice.entity.KnowledgeCategory;
import org.example.knowledgeservice.enumClass.ArticleStatus;

import java.time.LocalDateTime;

/** 知识库模块的转换方法：分类、文章。 */
public class KnowledgeConvert {

    public static CategoryResponseDTO categoryToResponse(KnowledgeCategory c){
        return CategoryResponseDTO.builder()
                .id(c.getId())
                .categoryName(c.getCategoryName())
                .build();
    }

    public static ArticleResponseDTO articleToResponse(KnowledgeArticle a,
                                                       String authorName,
                                                       String categoryName){
        return ArticleResponseDTO.builder()
                .id(a.getId())
                .title(a.getTitle())
                .content(a.getContent())
                .coverImage(a.getCoverImage())
                .categoryId(a.getCategoryId())
                .status(a.getStatus())
                .readCount(a.getReadCount())
                .summary(a.getSummary())
                .tags(a.getTags())
                .tagList(a.getTagList())     //实体里早就写好的拆分方法，之前一直没人调
                .publishedAt(a.getPublishedAt())
                .updatedAt(a.getUpdatedAt())
                .authorName(authorName)
                .categoryName(categoryName)
                .build();
    }

    public static KnowledgeArticle commandToEntity(ArticleCommandDTO cmd, Long authorId){
        return KnowledgeArticle.builder()
                .title(cmd.getTitle())
                .content(cmd.getContent())
                .coverImage(cmd.getCoverImage())
                .categoryId(cmd.getCategoryId())
                .summary(cmd.getSummary())
                .tags(cmd.getTags())
                .authorId(authorId)                       // ← 从 token 拿的
                .readCount(0)                             // ← 新文章从 0 开始
                .status(ArticleStatus.DRAFT.getCode())    // ← 新文章一律草稿
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    public static KnowledgeArticle toUpdateEntity(String id, ArticleCommandDTO cmd){
        return KnowledgeArticle.builder()
                .id(id)                                   // ← 从路径拿
                .title(cmd.getTitle())
                .content(cmd.getContent())
                .coverImage(cmd.getCoverImage())
                .categoryId(cmd.getCategoryId())
                .summary(cmd.getSummary())
                .tags(cmd.getTags())
                .updatedAt(LocalDateTime.now())
                // authorId / readCount / status / createdAt 都不填
                // → updateById 会跳过它们，保持原值
                .build();
    }
}
