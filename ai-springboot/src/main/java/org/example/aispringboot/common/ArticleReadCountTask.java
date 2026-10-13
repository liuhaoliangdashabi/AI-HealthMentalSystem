package org.example.aispringboot.common;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.extern.slf4j.Slf4j;
import org.example.aispringboot.entity.KnowledgeArticle;
import org.example.aispringboot.mapper.KnowledgeArticleMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
public class ArticleReadCountTask {

    private static final String READ_DELTA_KEY = "article:read:delta:";
    private static final String READ_DIRTY_KEY = "article:read:dirty";

    @Autowired
    private StringRedisTemplate redis;
    @Autowired
    private KnowledgeArticleMapper articleMapper;

    @Scheduled(fixedDelay = 30000)
    public void flushReadCount(){

        try {
            log.info("定时任务开始，阅读增量入库");
            Long articleCount=redis.opsForSet().size(READ_DIRTY_KEY);
            List<String> articleIds=redis.opsForSet().pop(READ_DIRTY_KEY,articleCount==null?0:articleCount);
            log.info("脏集合 size={}，pop 出 {} 条", articleCount, articleIds == null ? "null" : articleIds.size());
            if(articleIds==null ||articleIds.isEmpty())return;

            for(String articleId:articleIds){
                try{
                    String deltaStr=redis.opsForValue().getAndDelete(READ_DELTA_KEY + articleId);
                    if(deltaStr==null)continue;
                    Integer delta=Integer.valueOf(deltaStr);
                    if(delta.equals(0))continue;

                    articleMapper.update(null,new LambdaUpdateWrapper<KnowledgeArticle>()
                            .eq(KnowledgeArticle::getId,articleId)
                            .setSql("read_count = IFNULL(read_count,0) + "+delta));
                    log.debug("articleId={}文章阅读增量归档成功",articleId);
                }catch(Exception e){
                    log.warn("单点增量失败，放回脏集合，articleId={}",articleId,e);
                    redis.opsForSet().add(READ_DIRTY_KEY,articleId);
                }
            }
            log.info("定时任务完成");
        } catch (Exception e) {
            log.warn("定时任务增量失败，出现问题",e);
        }
    }
}
