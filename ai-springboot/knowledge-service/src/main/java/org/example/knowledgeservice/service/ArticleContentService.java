package org.example.knowledgeservice.service;

import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;

import org.example.knowledgeservice.DTO.response.ArticleResponseDTO;
import org.example.knowledgeservice.entity.KnowledgeArticle;
import org.example.knowledgeservice.entity.KnowledgeCategory;
import org.example.knowledgeservice.mapper.KnowledgeArticleMapper;
import org.example.knowledgeservice.mapper.KnowledgeCategoryMapper;
import org.example.knowledgeservice.service.convert.KnowledgeConvert;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class ArticleContentService {
    @Autowired
    private KnowledgeArticleMapper articleMapper;
    @Autowired
    private KnowledgeCategoryMapper categoryMapper;
    @Value("${oss.bucket}")
    private String ossBucket;
    @Value("${oss.region}")
    private String ossRegion;
    @Value("${oss.domain:}")
    private String ossDomain;
    @Cacheable(
            cacheNames="article",
            key="#id",//注意sync和unless冲突，unless等方法执行完才知道答案，sync把并发挡在方法外面
            unless="#result==null || #result.status!=T(org.example.knowledgeservice.enumClass.ArticleStatus).PUBLISHED.getCode()"
    )
    public ArticleResponseDTO loadDetail(String id){
        KnowledgeArticle a=articleMapper.selectById(id);
        if(a==null)return null;
//        User u=userMapper.selectById(a.getAuthorId());
//        if(u==null)log.warn("文章{}的作者{}不存在，可能是脏数据",id,a.getAuthorId());
        KnowledgeCategory c=categoryMapper.selectById(a.getCategoryId());
        if(c==null)log.warn("文章{}的分类{}不存在，可能是脏数据",id,a.getCategoryId());
        return withCoverUrl(KnowledgeConvert.articleToResponse(a,a.getAuthorName(),//u==null?null:u.getDisplayName(),
                c==null?null:c.getCategoryName()));
    }
    private ArticleResponseDTO withCoverUrl(ArticleResponseDTO dto){
        dto.setCoverImageUrl(buildCoverUrl(dto.getCoverImage()));
        return dto;
    }

    private String buildCoverUrl(String objectKey){
        if(objectKey==null || objectKey.isBlank())return null;
        if(objectKey.startsWith("http://") || objectKey.startsWith("https://"))
            return objectKey;
        String base=(ossDomain==null || ossDomain.isBlank())
                ?"https://"+ossBucket+".oss-"+ossRegion+".aliyuncs.com"
                :ossDomain;
        return base+"/"+objectKey.replaceAll("^/+","");
    }
}
