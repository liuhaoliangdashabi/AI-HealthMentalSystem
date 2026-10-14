package org.example.aispringboot.service;

import com.aliyun.sdk.service.oss2.operations.ObjectBasic;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.example.aispringboot.DTO.command.ArticleCommandDTO;
import org.example.aispringboot.DTO.command.ArticlePageQueryDTO;
import org.example.aispringboot.DTO.command.ArticleStatusCommandDTO;
import org.example.aispringboot.DTO.response.ArticleResponseDTO;
import org.example.aispringboot.DTO.response.UserLoginResponseDTO;
import org.example.aispringboot.common.Result;
import org.example.aispringboot.entity.KnowledgeArticle;
import org.example.aispringboot.entity.KnowledgeCategory;
import org.example.aispringboot.entity.User;
import org.example.aispringboot.enumClass.ArticleStatus;
import org.example.aispringboot.enumClass.UserType;
import org.example.aispringboot.exception.BusinessException;
import org.example.aispringboot.mapper.KnowledgeArticleMapper;
import org.example.aispringboot.mapper.KnowledgeCategoryMapper;
import org.example.aispringboot.mapper.UserMapper;
import org.example.aispringboot.service.convert.KnowledgeConvert;
import org.example.aispringboot.util.OSSUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
@Slf4j
@Service
public class KnowledgeArticleService {
    @Autowired
    private KnowledgeArticleMapper articleMapper;
    @Autowired
    private KnowledgeCategoryMapper categoryMapper;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private OSSUtil ossUtil;
    @Autowired
    private ArticleContentService contentService;
    @Autowired
    private StringRedisTemplate redis;

    private static final String READ_DELTA_KEY="article:read:delta:";
    private static final String READ_DIRTY_KEY="article:read:dirty";
    private static final String DAY_HOT_KEY="article:hot:";

    private static final int TOP=10;

    public IPage<ArticleResponseDTO> selectPage(ArticlePageQueryDTO query,
                                                UserLoginResponseDTO.UserDetailResponseDTO user) {
        boolean isAdmin= UserType.ADMIN.getCode().equals(user.getUserType());
        //AB端按前端传的 status 过滤；C端【强制】只看已发布 —— 不管前端传什么，防止自己拼参数绕过
        Integer scopedStatus=isAdmin?query.getStatus():ArticleStatus.PUBLISHED.getCode();

        Page<KnowledgeArticle> page=new Page<>(query.getCurrentPage(),query.getSize());
        LambdaQueryWrapper<KnowledgeArticle> qw=new LambdaQueryWrapper<>();
        qw.like(query.getTitle()!=null && !query.getTitle().isEmpty(),
                KnowledgeArticle::getTitle,query.getTitle());
        qw.eq(query.getCategoryId()!=null,
                KnowledgeArticle::getCategoryId,query.getCategoryId());
        qw.eq(scopedStatus!=null,
                KnowledgeArticle::getStatus,scopedStatus);

        //sortDirection 之前是"只写不读"——前端一直在传，后端一直忽略，永远只有降序。
        //前端 C端推荐位传 readCount+desc，列表页传 publishedAt+desc
        String sortField=query.getSortField()==null?"":query.getSortField();
        boolean asc="asc".equalsIgnoreCase(query.getSortDirection());
        switch(sortField){
            case "readCount"->{
                if(asc)qw.orderByAsc(KnowledgeArticle::getReadCount);
                else   qw.orderByDesc(KnowledgeArticle::getReadCount);
            }
            case "publishedAt"->{
                if(asc)qw.orderByAsc(KnowledgeArticle::getPublishedAt);
                else   qw.orderByDesc(KnowledgeArticle::getPublishedAt);
            }
            default ->{
                log.warn("没见过这种排序:{}",sortField);
                throw new BusinessException("不支持的排序字段");
            }
        }

        articleMapper.selectPage(page,qw);
        Map<String,ArticleResponseDTO> dtoMap=complementArticleDTO(page.getRecords());
        return page.convert(a->dtoMap.get(a.getId()));
    }

    public void createArticle(@Valid ArticleCommandDTO commandDTO,Long id) {
        if(categoryMapper.selectById(commandDTO.getCategoryId())==null){
            throw new BusinessException(commandDTO.getCategoryId()+"分类不存在");
        }
        KnowledgeArticle article=KnowledgeConvert.commandToEntity(commandDTO,id);
        articleMapper.insert(article);
    }

    @CacheEvict(cacheNames = "article", key = "#articleId")
    public void putArticleStatus(String articleId, ArticleStatusCommandDTO status){
        //★ 之前完全不校验 —— 前端传个 99 也会原样写进库。
        //  正好把一直没人调用的 ArticleStatus.isValidCode 用上
        if(!ArticleStatus.isValidCode(status.getStatus())){
            log.warn("文章{}要改成非法状态：{}",articleId,status.getStatus());
            throw new BusinessException("无效的文章状态");
        }
        LambdaUpdateWrapper<KnowledgeArticle> uw=new LambdaUpdateWrapper<>();
        uw.eq(KnowledgeArticle::getId,articleId)
                .set(KnowledgeArticle::getStatus, status.getStatus())
                .set(KnowledgeArticle::getUpdatedAt, LocalDateTime.now());
        articleMapper.update(uw);
    }


    public ArticleResponseDTO getArticleDetail(String id,
                                               UserLoginResponseDTO.UserDetailResponseDTO user) {
        ArticleResponseDTO dto=contentService.loadDetail(id);
        if(dto==null)throw new BusinessException("文章不存在");
        Integer status=dto.getStatus();
        //非管理员只能看已发布的。文案统一成"不存在"，不泄露草稿的存在
        boolean isAdmin= UserType.ADMIN.getCode().equals(user.getUserType());
        if(!isAdmin && !ArticleStatus.PUBLISHED.getCode().equals(status)){
            log.warn("userId={}无权查看未发布的文章{}，status={}",user.getId(),id,status);
            throw new BusinessException("文章不存在");
        }


        if(!isAdmin){
            //TODO(你)：写在这
            int delta=0;
            try {
                Long v=redis.opsForValue().increment(READ_DELTA_KEY+id);
                delta=(v==null?0:v.intValue());
            } catch (Exception e) {
                log.warn("新增文章阅读增量缓存失败，Redis挂了，快去修复",e);
            }
            try {
                redis.opsForSet().add(READ_DIRTY_KEY,id);
            } catch (Exception e) {
                log.warn("设置新阅读文章缓存失败，Redis挂了，快去修复",e);
            }
            dto.setReadCount((dto.getReadCount()==null?0:dto.getReadCount())+delta);
            try{
                final LocalDate NOW=LocalDate.now();
                redis.opsForZSet().incrementScore(DAY_HOT_KEY+NOW,dto.getId(),1);
                redis.expire(DAY_HOT_KEY+NOW, Duration.ofDays(8));
            }catch(Exception e){
                log.warn("设置文章日热度缓存失败，Redis挂了，快去修复");
            }
        }


        return dto;
    }

    @CacheEvict(cacheNames = "article", key = "#articleId")
    public void putArticleDetail(String articleId, @Valid ArticleCommandDTO commandDTO) {
        KnowledgeArticle entity=KnowledgeConvert.toUpdateEntity(articleId,commandDTO);
        int count=articleMapper.updateById(entity);
        if(count==0){
            log.warn("文章{}不存在",articleId);
            throw new BusinessException("文章不存在");
        }
        log.debug("文章已更新：articleId={}",articleId);
    }

    @CacheEvict(cacheNames = "article", key = "#articleId")
    public void deleteArticle(String articleId) {
        int count=articleMapper.deleteById(articleId);
        if(count==0){
            log.warn("文章{}不存在",articleId);
            throw new BusinessException("文章不存在");
        }
    }

    public List<ArticleResponseDTO> getHotArticle() {
        Set<ZSetOperations.TypedTuple<String>> tuples;
        try {
            //榜单可能混着草稿/已删除文章，取2倍做冗余
            tuples=redis.opsForZSet().reverseRangeWithScores("article:hot:7d",0,TOP*2-1);
        } catch (Exception e) {
            log.warn("获取热点文本列表失败，Redis挂了，快去修复",e);
            return List.of();
        }
        if(tuples==null || tuples.isEmpty())return List.of();

        List<String> hotIds=tuples.stream().map(ZSetOperations.TypedTuple::getValue).toList();
        List<KnowledgeArticle> hotPublishedArticles=articleMapper.selectBatchIds(hotIds).stream()
                .filter(a->ArticleStatus.PUBLISHED.getCode().equals(a.getStatus()))
                .toList();

        Map<String,ArticleResponseDTO> dtoMap=complementArticleDTO(hotPublishedArticles);
        return hotIds.stream()
                .map(dtoMap::get)
                .filter(Objects::nonNull)
                .limit(TOP)
                .toList();
    }

    private ArticleResponseDTO withCoverUrl(ArticleResponseDTO dto){
        dto.setCoverImageUrl(ossUtil.getUrl(dto.getCoverImage()));
        return dto;
    }

    private Map<String,ArticleResponseDTO> complementArticleDTO(List<KnowledgeArticle> articles){
        if(articles==null || articles.isEmpty())return Map.of();


        Set<Long> authorIds=articles.stream()
                .map(KnowledgeArticle::getAuthorId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long,String> authorNameMap=authorIds.isEmpty()?Map.of():
                userMapper.selectBatchIds(authorIds).stream()
                        .collect(Collectors.toMap(User::getId, User::getDisplayName));
        authorIds.removeAll(authorNameMap.keySet());
        if(!authorIds.isEmpty())log.warn("这类作者在库中查不到数据，可能是脏数据:{}",authorIds);

        Set<Long> categoryIds=articles.stream()
                .map(KnowledgeArticle::getCategoryId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long,String> categoryNameMap=categoryIds.isEmpty()?Map.of():
                categoryMapper.selectBatchIds(categoryIds).stream()
                        .collect(Collectors.toMap(KnowledgeCategory::getId,KnowledgeCategory::getCategoryName));
        categoryIds.removeAll(categoryNameMap.keySet());
        if(!categoryIds.isEmpty())log.warn("这类分类在库中查不到数据，可能是脏数据:{}",categoryIds);

        return articles.stream().collect(Collectors.
                toMap(KnowledgeArticle::getId,
                        a->withCoverUrl(KnowledgeConvert.articleToResponse(a,
                                authorNameMap.get(a.getAuthorId()),
                                categoryNameMap.get(a.getCategoryId())))));
    }
}

