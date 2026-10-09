package org.example.aispringboot.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.extern.slf4j.Slf4j;
import org.example.aispringboot.DTO.response.CategoryResponseDTO;
import org.example.aispringboot.entity.KnowledgeArticle;
import org.example.aispringboot.entity.KnowledgeCategory;
import org.example.aispringboot.exception.BusinessException;
import org.example.aispringboot.mapper.KnowledgeArticleMapper;
import org.example.aispringboot.mapper.KnowledgeCategoryMapper;
import org.example.aispringboot.service.convert.KnowledgeConvert;
import org.springframework.data.redis.RedisConnectionFailureException;
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

    //分类树读多写少、变更极少、就一棵 → 全量一个 key 搞定，不会分页 key 爆炸
    private static final String CATEGORY_TREE_KEY="cache:category:tree";
    private static final Duration CATEGORY_TREE_TTL=Duration.ofMinutes(10);

    public KnowledgeCategoryService(KnowledgeCategoryMapper categoryMapper,
                                    KnowledgeArticleMapper articleMapper,
                                    RedisTemplate<String,Object> redisTemplate){
        this.articleMapper=articleMapper;
        this.categoryMapper=categoryMapper;
        this.redisTemplate=redisTemplate;
    }

    @SuppressWarnings("unchecked")//见 ①
    public List<CategoryResponseDTO> listAll(){
        //① 查缓存 → 命中就 log.debug 一行 + 强转返回，一个字节都不碰数据库
        //TODO(你) ①：get() 返回 Object，所以强转必须写（不写编译不过）
        List<CategoryResponseDTO> dto=(List<CategoryResponseDTO>) redisTemplate.opsForValue().get(CATEGORY_TREE_KEY);
        log.debug("从缓存中获取分类树，共{}个",dto==null?0:dto.size());
        if(dto!=null)return dto;
        //② 未命中 → 查库（这两行是原有的，原样保留，别删）
        List<KnowledgeCategory> list=categoryMapper.selectList(null);
        //TODO(你) ②：转成 List<CategoryResponseDTO>
        //   坑：别把 stream().toList() 的结果直接塞 Redis
        dto=list.stream()
                .map(KnowledgeConvert::categoryToResponse)
                .collect(Collectors.toCollection(ArrayList::new));
        //TODO(你) ③：回填缓存 —— set 的三参重载，TTL 用 CATEGORY_TREE_TTL
        //   想一想：TTL 要是不带，删分类那条路径兜不兜得住？
        redisTemplate.opsForValue().set(CATEGORY_TREE_KEY,dto,CATEGORY_TREE_TTL);
        //TODO(你) ④：返回
        return dto;

    }

    //分类数据变了 → 整棵树缓存作废，下次请求自然重建
    private void evictCategoryTreeCache(){
        //TODO(你)：删掉 CATEGORY_TREE_KEY
        redisTemplate.delete(CATEGORY_TREE_KEY);
    }

    @Transactional
    public void deleteCategory(Long categoryId){
        if(DEFAULT_CATEGORY_ID.equals(categoryId)){
            throw new BusinessException("默认分类不能删除");
        }

        KnowledgeCategory category=categoryMapper.selectById(categoryId);
        if(category==null)throw new BusinessException("要删除的分类不存在");

        LambdaUpdateWrapper<KnowledgeArticle> uw=new LambdaUpdateWrapper<>();
        uw.eq(KnowledgeArticle::getCategoryId,categoryId)
                .set(KnowledgeArticle::getCategoryId,DEFAULT_CATEGORY_ID);
        int moved=articleMapper.update(null,uw);
        categoryMapper.deleteById(categoryId);
        //TODO(你)：数据变了，缓存要作废。放这行，还是放别处？(它现在在 @Transactional 里，有隐患)
        evictCategoryTreeCache();
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

        LambdaUpdateWrapper<KnowledgeArticle> uw=new LambdaUpdateWrapper<>();
        uw.eq(KnowledgeArticle::getCategoryId,categoryId)
                .set(KnowledgeArticle::getCategoryId,goToId);
        int moved=articleMapper.update(null,uw);
        categoryMapper.deleteById(categoryId);
        //TODO(你)：数据变了，缓存要作废。放这行，还是放别处？(它现在在 @Transactional 里，有隐患)
        try{
            evictCategoryTreeCache();
        }catch(RedisConnectionFailureException e){
            log.warn("分类树缓存失效失败，key={}，等TTL自然过期",CATEGORY_TREE_KEY,e);
        }
        log.info("删除分类{}成功，挪走了{}篇文章到{}分类",categoryId,moved,goToId);
    }
}
