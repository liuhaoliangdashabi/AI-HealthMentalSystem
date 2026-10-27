package org.example.aispringboot.Test;

import org.example.aispringboot.entity.KnowledgeArticle;
import org.example.aispringboot.entity.KnowledgeCategory;
import org.example.aispringboot.mapper.KnowledgeArticleMapper;
import org.example.aispringboot.mapper.KnowledgeCategoryMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class MapperSmokeTest {

    @Autowired
    private KnowledgeArticleMapper articleMapper;

    @Autowired
    private KnowledgeCategoryMapper categoryMapper;

    @Test
    void throughDataBase() {
        KnowledgeCategory c = categoryMapper.selectById(1L);
        System.out.println("分类：" + c.getCategoryName());

        KnowledgeArticle a = articleMapper.selectById("550e8400-e29b-41d4-a716-446655440001");
        System.out.println("文章：" + a.getTitle()
                + " / 状态：" + a.getStatusDisplayName()
                + " / 标签：" + a.getTagList());
    }
}
