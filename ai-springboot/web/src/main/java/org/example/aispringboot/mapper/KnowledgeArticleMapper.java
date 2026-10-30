package org.example.aispringboot.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.example.aispringboot.entity.KnowledgeArticle;

/**
 * 知识域查询已迁到 knowledge-service，这里只留阅读数归档任务（ArticleReadCountTask）需要的 BaseMapper 能力。
 */
@Mapper
public interface KnowledgeArticleMapper extends BaseMapper<KnowledgeArticle> {
}
