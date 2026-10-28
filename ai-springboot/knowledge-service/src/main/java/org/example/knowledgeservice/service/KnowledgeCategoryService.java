package org.example.knowledgeservice.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.extern.slf4j.Slf4j;
import org.example.common.BusinessException;
import org.example.knowledgeservice.DTO.response.CategoryResponseDTO;
import org.example.knowledgeservice.entity.KnowledgeArticle;
import org.example.knowledgeservice.entity.KnowledgeCategory;
import org.example.knowledgeservice.mapper.KnowledgeArticleMapper;
import org.example.knowledgeservice.mapper.KnowledgeCategoryMapper;
import org.example.knowledgeservice.service.convert.KnowledgeConvert;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class KnowledgeCategoryService {
    private final KnowledgeCategoryMapper categoryMapper;
    private final KnowledgeArticleMapper articleMapper;
    private final RedisTemplate<String,Object> redisTemplate;
    private static final Long DEFAULT_CATEGORY_ID=1L;
    private final CacheManager cacheManager;

    //分类树读多写少、变更极少、就一棵 → 全量一个 key 搞定，不会分页 key 爆炸
    private static final String CATEGORY_TREE_KEY="cache:category:tree";
    private static final Duration CATEGORY_TREE_TTL=Duration.ofMinutes(10);
    private static final String ARTICLE_CACHE_NAME="article";

    public KnowledgeCategoryService(KnowledgeCategoryMapper categoryMapper,
                                    KnowledgeArticleMapper articleMapper,
                                    RedisTemplate<String,Object> redisTemplate,
                                    CacheManager cacheManager){
        this.articleMapper=articleMapper;
        this.categoryMapper=categoryMapper;
        this.redisTemplate=redisTemplate;
        this.cacheManager=cacheManager;
    }

    @SuppressWarnings("unchecked")//见 ①
    public List<CategoryResponseDTO> listAll(){
        //get返回的是Object，强转躲不掉的
        List<CategoryResponseDTO> dto= null;
        try {
            dto = (List<CategoryResponseDTO>) redisTemplate.opsForValue().get(CATEGORY_TREE_KEY);
            log.debug("从缓存中获取分类树，共{}个",dto==null?0:dto.size());
        } catch (Exception e) {
            log.warn("缓存获取分类树失败，Redis出问题了，尽快修复",e);
        }

        if(dto!=null)return dto;
        List<KnowledgeCategory> list=categoryMapper.selectList(null);
        //不能把stream().toList()的结果塞进来：JDK内部类写出去不带类型消息，读回来必炸
        dto=list.stream()
                .map(KnowledgeConvert::categoryToResponse)
                .collect(Collectors.toCollection(ArrayList::new));
        //回填缓存，如果evict失败，这是唯一兜底
        try {
            redisTemplate.opsForValue().set(CATEGORY_TREE_KEY,dto,CATEGORY_TREE_TTL);
        } catch (Exception e) {
            log.warn("回填缓存失效，Redis出问题了，尽快修复",e);
        }
        return dto;

    }

    //分类数据变了->整棵树缓存作废，下次请求自然重建
    private void evictCategoryTreeCache(){
        try{
            redisTemplate.delete(CATEGORY_TREE_KEY);
        }catch(Exception e){
            log.warn("分类树缓存失效失败，key={}，等TTL自然过期",CATEGORY_TREE_KEY,e);
        }
    }

    private void evictArticleCache(List<String> articleIds){
        Cache cache=cacheManager.getCache(ARTICLE_CACHE_NAME);
        if(cache==null){
            log.warn("拿不到{}缓存，文章缓存本次不生效，等TTL自然过期",ARTICLE_CACHE_NAME);
            return;
        }
        for(String id:articleIds){
            try{
                cache.evict(id);
            }catch(Exception e){
                log.warn("文章缓存失效失败，id={}，等TTL自然过期",id,e);
            }
        }
    }

    private void evictAfterCommit(List<String> articleIds){
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                evictCategoryTreeCache();
                evictArticleCache(articleIds);
            }
        });
    }


    @Transactional
    public void deleteCategory(Long categoryId){
        if(DEFAULT_CATEGORY_ID.equals(categoryId)){
            throw new BusinessException("默认分类不能删除");
        }

        KnowledgeCategory category=categoryMapper.selectById(categoryId);
        if(category==null)throw new BusinessException("要删除的分类不存在");

        //先捞受影响的文章
        List<KnowledgeArticle> affectedArticles=articleMapper.selectList(
                new LambdaQueryWrapper<KnowledgeArticle>()
                        .select(KnowledgeArticle::getId)
                        .eq(KnowledgeArticle::getCategoryId,categoryId));
        List<String> affectedArticleIds=affectedArticles.stream()
                .map(a->String.valueOf(a.getId())).toList();

        LambdaUpdateWrapper<KnowledgeArticle> uw=new LambdaUpdateWrapper<>();
        uw.eq(KnowledgeArticle::getCategoryId,categoryId)
                .set(KnowledgeArticle::getCategoryId,DEFAULT_CATEGORY_ID);
        int moved=articleMapper.update(null,uw);
        categoryMapper.deleteById(categoryId);
        evictAfterCommit(affectedArticleIds);
        log.info("删除分类{}成功，挪走了{}篇文章",categoryId,moved);
    }

    @Transactional
    public void deleteCategory(Long categoryId, Long goToId) {
        if(DEFAULT_CATEGORY_ID.equals(categoryId)){
            throw new BusinessException("默认分类不能删除");
        }

        KnowledgeCategory category=categoryMapper.selectById(categoryId);
        if(category==null)throw new BusinessException("要删除的分类不存在");
        KnowledgeCategory goToCategory=categoryMapper.selectById(goToId);
        if(goToCategory==null)throw new BusinessException("目标分类不存在");

        //先捞受影响的文章
        List<KnowledgeArticle> affectedArticles=articleMapper.selectList(
                new LambdaQueryWrapper<KnowledgeArticle>()
                        .select(KnowledgeArticle::getId)
                        .eq(KnowledgeArticle::getCategoryId,categoryId));
        List<String> affectedArticleIds=affectedArticles.stream()
                .map(a->String.valueOf(a.getId())).toList();

        LambdaUpdateWrapper<KnowledgeArticle> uw=new LambdaUpdateWrapper<>();
        uw.eq(KnowledgeArticle::getCategoryId,categoryId)
                .set(KnowledgeArticle::getCategoryId,goToId);
        int moved=articleMapper.update(null,uw);
        categoryMapper.deleteById(categoryId);
        evictAfterCommit(affectedArticleIds);
        log.info("删除分类{}成功，挪走了{}篇文章到{}分类",categoryId,moved,goToId);
    }
}
