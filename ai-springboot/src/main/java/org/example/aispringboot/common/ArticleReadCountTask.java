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

    //【第 2 关 · 你】两个 key，必须和 KnowledgeArticleService 里【一字不差】
    private static final String READ_DELTA_KEY = "article:read:delta:";
    private static final String READ_DIRTY_KEY = "article:read:dirty";

    @Autowired
    private StringRedisTemplate redis;
    @Autowired
    private KnowledgeArticleMapper articleMapper;

    // 调试期用 fixedDelay：没有六段 cron 的坑，而且【启动就跑】，不用等整点
    // 调通之后再换 cron，比如 "0 */5 * * * ?"（= 每 5 分钟）
    @Scheduled(fixedDelay = 30000)
    public void flushReadCount(){

        try {
            //TODO(你) ⓞ 先打一行日志说自己进来了。
            //   否则它只要提前 return，你在外面根本不知道它跑没跑过。
            log.info("定时任务开始，阅读增量入库");
            //TODO(你) ① 认领：pop(READ_DIRTY_KEY, 1000)
            //   为什么用 pop 不用 members？回写是【相对更新 +delta】，不能重放 ——
            //   两个实例读到同一批会【重复计数】。pop 是原子的"认领"。
            //   ⚠️ pop(key) 单参只弹【一个】，要用两参版本
            Long articleCount=redis.opsForSet().size(READ_DIRTY_KEY);
            List<String> articleIds=redis.opsForSet().pop(READ_DIRTY_KEY,articleCount==null?0:articleCount);
            log.info("脏集合 size={}，pop 出 {} 条", articleCount, articleIds == null ? "null" : articleIds.size());
            //TODO(你) ② 空了就 return
            if(articleIds==null ||articleIds.isEmpty())return;
            //TODO(你) ③ for 循环逐篇处理（别用 Stream —— 这个位置需要"每一篇都能单独失败"）
            //     a) getAndDelete(READ_DELTA_KEY + id)  → 返回【String】，可能是 null
            //        ⚠️ 是 String 不是 Long！StringRedisTemplate 存的本来就是字符串
            //     b) null → continue
            //     c) parseLong 拿到 delta；delta == 0 → continue
            //     d) UPDATE knowledge_article SET read_count = read_count + <delta> WHERE id = ?
            //     e) log.debug 记一笔，方便验收
            for(String articleId:articleIds){
                log.info("▶ id=[{}] len={}  →  key=[{}] len={}",
                        articleId, articleId.length(),
                        READ_DELTA_KEY + articleId, (READ_DELTA_KEY + articleId).length());
                try{
                    String deltaStr=redis.opsForValue().getAndDelete(READ_DELTA_KEY + articleId);
                    if(deltaStr==null)continue;
                    Integer delta=Integer.valueOf(deltaStr);
                    if(delta.equals(0))continue;
                    articleMapper.update(null,new LambdaUpdateWrapper<KnowledgeArticle>()
                            .eq(KnowledgeArticle::getId,articleId)
                            .setSql("read_count = read_count + "+delta));
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
        //TODO(你) ④ catch 放在【循环里面】
        //   某篇炸了，后面几篇还要继续处理。
        //   放循环外面 → 一篇异常 = 后面全不处理，而它们已经被 pop 认领走了 → 永久丢失
    }
}
