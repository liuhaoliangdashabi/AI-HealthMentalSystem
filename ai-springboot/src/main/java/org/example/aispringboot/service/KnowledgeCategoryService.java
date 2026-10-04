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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
public class KnowledgeCategoryService {
    private final KnowledgeCategoryMapper categoryMapper;
    private final KnowledgeArticleMapper articleMapper;
    private static final Long DEFAULT_CATEGORY_ID=1L;
    public KnowledgeCategoryService(KnowledgeCategoryMapper categoryMapper,
                                    KnowledgeArticleMapper articleMapper){
        this.articleMapper=articleMapper;
        this.categoryMapper=categoryMapper;
    }

    public List<CategoryResponseDTO> listAll(){
        List<KnowledgeCategory> list=categoryMapper.selectList(null);
        return list.stream().map(KnowledgeConvert::categoryToResponse).toList();
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

        log.info("删除分类{}成功，挪走了{}篇文章到{}分类",categoryId,moved,goToId);
    }
}
