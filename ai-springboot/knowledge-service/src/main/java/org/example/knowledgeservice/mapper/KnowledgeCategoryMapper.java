package org.example.knowledgeservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.example.knowledgeservice.entity.KnowledgeCategory;

@Mapper
public interface KnowledgeCategoryMapper extends BaseMapper<KnowledgeCategory> {
}
