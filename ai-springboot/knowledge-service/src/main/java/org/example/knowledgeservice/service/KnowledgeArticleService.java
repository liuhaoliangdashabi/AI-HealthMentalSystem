package org.example.knowledgeservice.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;

import org.example.common.BusinessException;
import org.example.knowledgeservice.DTO.command.ArticleCommandDTO;
import org.example.knowledgeservice.DTO.command.ArticlePageQueryDTO;
import org.example.knowledgeservice.DTO.command.ArticleStatusCommandDTO;
import org.example.knowledgeservice.DTO.response.ArticleResponseDTO;
import org.example.knowledgeservice.entity.KnowledgeArticle;
import org.example.knowledgeservice.entity.KnowledgeCategory;
import org.example.knowledgeservice.enumClass.ArticleStatus;
import org.example.knowledgeservice.mapper.KnowledgeArticleMapper;
import org.example.knowledgeservice.mapper.KnowledgeCategoryMapper;
import org.example.knowledgeservice.service.convert.KnowledgeConvert;
import org.example.knowledgeservice.util.RedisKeys;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

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
    private ArticleContentService contentService;
    @Autowired
    private StringRedisTemplate redis;
    @Value("${oss.bucket}")
    private String ossBucket;
    @Value("${oss.region}")
    private String ossRegion;
    @Value("${oss.domain:}")
    private String ossDomain;

    private static final String READ_DELTA_KEY=RedisKeys.ARTICLE_READ_DELTA_PREFIX;
    private static final String READ_DIRTY_KEY=RedisKeys.ARTICLE_READ_DIRTY;
    private static final String DAY_HOT_KEY= RedisKeys.ARTICLE_HOT_DAY_PREFIX;

    private static final int TOP=10;

    public IPage<ArticleResponseDTO> selectPage(ArticlePageQueryDTO query,
                                                Integer roleType) {
        boolean isAdmin= Integer.valueOf(2).equals(roleType);
        //AB端按前端传的 status 过滤；C端强制只看已发布，不管前端传什么，防止自己拼参数绕过
        Integer scopedStatus=isAdmin?query.getStatus(): ArticleStatus.PUBLISHED.getCode();

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
            //空串表示前端没传排序字段，走默认排序，不能当非法值处理
            //  管理端「知识文章」列表压根不传 sortField，之前直接落到 default 抛
            //  「不支持的排序字段」，整个列表页打不开 —— 把"没传"和"传错了"混为一谈。
            case ""->{
                qw.orderByDesc(KnowledgeArticle::getPublishedAt);
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

    public void createArticle(@Valid ArticleCommandDTO commandDTO, Long id) {
        if(categoryMapper.selectById(commandDTO.getCategoryId())==null){
            throw new BusinessException(commandDTO.getCategoryId()+"分类不存在");
        }
        KnowledgeArticle article= KnowledgeConvert.commandToEntity(commandDTO,id);
        articleMapper.insert(article);
    }

    @CacheEvict(cacheNames = "article", key = "#articleId")
    public void putArticleStatus(String articleId, ArticleStatusCommandDTO status){
        //之前完全不校验，前端传个 99 也会原样写进库
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
                                               Integer roleType) {
        ArticleResponseDTO dto=contentService.loadDetail(id);
        if(dto==null)throw new BusinessException("文章不存在");
        Integer status=dto.getStatus();
        //非管理员只能看已发布的。文案统一成"不存在"，不泄露草稿的存在
        boolean isAdmin= Integer.valueOf(2).equals(roleType);
        if(!isAdmin && !ArticleStatus.PUBLISHED.getCode().equals(status)){
            log.info("roleType={}无权查看未发布的文章{}，status={}",roleType,id,status);
            throw new BusinessException("文章不存在");
        }


        if(!isAdmin){
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

    /**
     * 推荐阅读：7天热度榜优先，不足用最新发布的文章补齐。
     * 热度榜只有被读过的文章才进榜，新部署或当天无人访问时榜可能是空的，所以必须补齐。
     * Redis挂了也走补齐分支，推荐位是首页门面，宁可退化成最新列表也不能整块消失。
     */
    public List<ArticleResponseDTO> getHotArticle() {
        //1. 取榜单 ID（Redis 异常 → 空列表，后面自然走补齐）
        List<String> hotIds = new ArrayList<>();
        try {
            Set<ZSetOperations.TypedTuple<String>> tuples =
                    redis.opsForZSet().reverseRangeWithScores(RedisKeys.ARTICLE_HOT_WINDOW, 0, TOP * 2 - 1);
            if (tuples != null) {
                hotIds = tuples.stream()
                        .map(ZSetOperations.TypedTuple::getValue)
                        .filter(Objects::nonNull)
                        .toList();
            }
        } catch (Exception e) {
            log.warn("读取热度榜失败，降级为按发布时间补齐", e);
        }

        //2. 按榜单顺序查出「已发布」的（榜里可能混着草稿/已删除）
        List<KnowledgeArticle> ordered = new ArrayList<>();
        if (!hotIds.isEmpty()) {
            Map<String, KnowledgeArticle> byId = articleMapper.selectBatchIds(hotIds).stream()
                    .filter(a -> ArticleStatus.PUBLISHED.getCode().equals(a.getStatus()))
                    .collect(Collectors.toMap(KnowledgeArticle::getId, a -> a));
            hotIds.stream()
                    .map(byId::get)
                    .filter(Objects::nonNull)
                    .limit(TOP)
                    .forEach(ordered::add);
        }

        //3. 不足 TOP 条，用最新发布的补齐（排除已经在榜里的，别重复）
        if (ordered.size() < TOP) {
            Set<String> existIds = ordered.stream()
                    .map(KnowledgeArticle::getId)
                    .collect(Collectors.toSet());
            List<KnowledgeArticle> fill = articleMapper.selectList(
                    new LambdaQueryWrapper<KnowledgeArticle>()
                            .eq(KnowledgeArticle::getStatus, ArticleStatus.PUBLISHED.getCode())
                            .notIn(!existIds.isEmpty(), KnowledgeArticle::getId, existIds)
                            .orderByDesc(KnowledgeArticle::getPublishedAt)
                            .last("limit " + (TOP - ordered.size())));
            ordered.addAll(fill);
        }

        if (ordered.isEmpty()) {
            return List.of();
        }
        Map<String, ArticleResponseDTO> dtoMap = complementArticleDTO(ordered);
        return ordered.stream()
                .map(a -> dtoMap.get(a.getId()))
                .filter(Objects::nonNull)
                .toList();
    }

    private ArticleResponseDTO withCoverUrl(ArticleResponseDTO dto){
        dto.setCoverImageUrl(buildCoverUrl(dto.getCoverImage()));
        return dto;
    }

    private Map<String,ArticleResponseDTO> complementArticleDTO(List<KnowledgeArticle> articles){
        if(articles==null || articles.isEmpty())return Map.of();
//        Set<Long> authorIds=articles.stream()
//                .map(KnowledgeArticle::getAuthorId)
//                .filter(Objects::nonNull)
//                .collect(Collectors.toSet());
//        Map<Long,String> authorNameMap=authorIds.isEmpty()?Map.of():
//                userMapper.selectBatchIds(authorIds).stream()
//                        .collect(Collectors.toMap(User::getId, User::getDisplayName));
//        authorIds.removeAll(authorNameMap.keySet());
//        if(!authorIds.isEmpty())log.warn("这类作者在库中查不到数据，可能是脏数据:{}",authorIds);

        Set<Long> categoryIds=articles.stream()
                .map(KnowledgeArticle::getCategoryId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long,String> categoryNameMap=categoryIds.isEmpty()?Map.of():
                categoryMapper.selectBatchIds(categoryIds).stream()
                        .collect(Collectors.toMap(KnowledgeCategory::getId, KnowledgeCategory::getCategoryName));
        categoryIds.removeAll(categoryNameMap.keySet());
        if(!categoryIds.isEmpty())log.warn("这类分类在库中查不到数据，可能是脏数据:{}",categoryIds);

        return articles.stream().collect(Collectors.
                toMap(KnowledgeArticle::getId,
                        a->withCoverUrl(KnowledgeConvert.articleToResponse(a,
                                a.getAuthorName(),
                                categoryNameMap.get(a.getCategoryId())))));
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

