package org.example.aispringboot.service;

import lombok.extern.slf4j.Slf4j;
import org.example.aispringboot.DTO.response.ArticleResponseDTO;
import org.example.aispringboot.entity.KnowledgeArticle;
import org.example.aispringboot.entity.KnowledgeCategory;
import org.example.aispringboot.entity.User;
import org.example.aispringboot.mapper.KnowledgeArticleMapper;
import org.example.aispringboot.mapper.KnowledgeCategoryMapper;
import org.example.aispringboot.mapper.UserMapper;
import org.example.aispringboot.service.convert.KnowledgeConvert;
import org.example.aispringboot.util.OSSUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
@Slf4j
@Service
public class ArticleContentService {
    @Autowired
    private KnowledgeArticleMapper articleMapper;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private KnowledgeCategoryMapper categoryMapper;
    @Autowired
    private OSSUtil ossUtil;
    @Cacheable(
            cacheNames="article",
            key="#id",//注意sync和unless冲突，unless等方法执行完才知道答案，sync把并发挡在方法外面
            unless="#result!=null && #result.status!=T(org.example.aispringboot.enumClass.ArticleStatus).PUBLISHED.getCode()"
    )
    public ArticleResponseDTO loadDetail(String id){
        KnowledgeArticle a=articleMapper.selectById(id);
        if(a==null)return null;
        User u=userMapper.selectById(a.getAuthorId());
        if(u==null)log.warn("文章{}的作者{}不存在，可能是脏数据",id,a.getAuthorId());
        KnowledgeCategory c=categoryMapper.selectById(a.getCategoryId());
        if(c==null)log.warn("文章{}的分类{}不存在，可能是脏数据",id,a.getCategoryId());
        return withCoverUrl(KnowledgeConvert.articleToResponse(a,u==null?null:u.getDisplayName(),
                c==null?null:c.getCategoryName()));
    }
    private ArticleResponseDTO withCoverUrl(ArticleResponseDTO dto){
        dto.setCoverImageUrl(ossUtil.getUrl(dto.getCoverImage()));
        return dto;
    }
}
