package org.example.aispringboot.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.example.aispringboot.DTO.command.ArticlePageQueryDTO;
import org.example.aispringboot.DTO.response.ArticleResponseDTO;
import org.example.aispringboot.entity.KnowledgeArticle;
@Mapper
public interface KnowledgeArticleMapper extends BaseMapper<KnowledgeArticle> {
}
