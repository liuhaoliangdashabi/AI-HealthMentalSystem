package org.example.aispringboot.service;

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
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;

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

        String sortField=query.getSortField()==null?"":query.getSortField();
        switch(sortField){
            case "readCount"->qw.orderByDesc(KnowledgeArticle::getReadCount);
            default         ->qw.orderByDesc(KnowledgeArticle::getPublishedAt);
        }

        articleMapper.selectPage(page,qw);

        List<KnowledgeArticle> articles=page.getRecords();
        if(articles.isEmpty()){
            return page.convert(a->null);
        }

        Set<Long> authorIds=articles.stream()
                .map(KnowledgeArticle::getAuthorId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long,String> authorNameMap=authorIds.isEmpty()?Map.of():
                userMapper.selectBatchIds(authorIds).stream()
                        .collect(Collectors.toMap(User::getId,User::getDisplayName));
        authorIds.removeAll(authorNameMap.keySet());
        if(!authorIds.isEmpty())log.warn("这些作者id不存在，可能是脏数据：{}",authorIds);


        Set<Long> categoryIds=articles.stream()
                .map(KnowledgeArticle::getCategoryId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long,String> categoryNameMap=categoryIds.isEmpty()?Map.of():
                categoryMapper.selectBatchIds(categoryIds).stream()
                        .collect(Collectors.toMap(KnowledgeCategory::getId, KnowledgeCategory::getCategoryName));
        categoryIds.removeAll(categoryNameMap.keySet());
        if(!categoryIds.isEmpty())log.warn("这些作者id不存在，可能是脏数据:{}",categoryIds);


        return page.convert(a-> withCoverUrl(KnowledgeConvert.articleToResponse(
                    a,authorNameMap.get(a.getAuthorId()),
                    categoryNameMap.get(a.getCategoryId())))
        );
    }

    public void createArticle(@Valid ArticleCommandDTO commandDTO,Long id) {
        if(categoryMapper.selectById(commandDTO.getCategoryId())==null){
            throw new BusinessException(commandDTO.getCategoryId()+"分类不存在");
        }
        KnowledgeArticle article=KnowledgeConvert.commandToEntity(commandDTO,id);
        articleMapper.insert(article);
    }

    public void putArticleStatus(String articleId, ArticleStatusCommandDTO status){
        LambdaUpdateWrapper<KnowledgeArticle> uw=new LambdaUpdateWrapper<>();
        uw.eq(KnowledgeArticle::getId,articleId)
                .set(KnowledgeArticle::getStatus, status.getStatus())
                .set(KnowledgeArticle::getUpdatedAt, LocalDateTime.now());
        articleMapper.update(uw);
    }

    public ArticleResponseDTO getArticleDetail(String id,
                                               UserLoginResponseDTO.UserDetailResponseDTO user) {
        KnowledgeArticle a=articleMapper.selectById(id);
        if(a==null)throw new BusinessException("文章不存在");
        //非管理员只能看已发布的。文案统一成"不存在"，不泄露草稿的存在
        boolean isAdmin= UserType.ADMIN.getCode().equals(user.getUserType());
        if(!isAdmin && !ArticleStatus.PUBLISHED.getCode().equals(a.getStatus())){
            log.warn("userId={}无权查看未发布的文章{}，status={}",user.getId(),id,a.getStatus());
            throw new BusinessException("文章不存在");
        }
        User u=userMapper.selectById(a.getAuthorId());
        if(u==null)log.warn("文章{}的作者{}不存在，可能是脏数据",id,a.getAuthorId());
        KnowledgeCategory c=categoryMapper.selectById(a.getCategoryId());
        if(c==null)log.warn("文章{}的分类{}不存在，可能是脏数据",id,a.getCategoryId());
        return withCoverUrl(KnowledgeConvert.articleToResponse(a,u==null?null:u.getDisplayName(),
                c==null?null:c.getCategoryName()));
    }

    public void putArticleDetail(String articleId, @Valid ArticleCommandDTO commandDTO) {
        KnowledgeArticle entity=KnowledgeConvert.toUpdateEntity(articleId,commandDTO);
        int count=articleMapper.updateById(entity);
        if(count==0){
            log.warn("文章{}不存在",articleId);
            throw new BusinessException("文章不存在");
        }
        log.debug("文章已更新：articleId={}",articleId);
    }

    public void deleteArticle(String articleId) {
        int count=articleMapper.deleteById(articleId);
        if(count==0){
            log.warn("文章{}不存在",articleId);
            throw new BusinessException("文章不存在");
        }
    }

    //coverImage 存的是 OSS 相对路径，这里换算成能直接访问的完整 URL 填进 coverImageUrl
    //（历史数据里可能已经存了完整 URL，OSSUtil.getUrl 会原样返回，不会重复拼）
    private ArticleResponseDTO withCoverUrl(ArticleResponseDTO dto){
        dto.setCoverImageUrl(ossUtil.getUrl(dto.getCoverImage()));
        return dto;
    }
}

