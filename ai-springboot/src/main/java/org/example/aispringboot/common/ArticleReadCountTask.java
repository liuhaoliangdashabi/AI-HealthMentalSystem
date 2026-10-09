package org.example.aispringboot.common;

import lombok.extern.slf4j.Slf4j;
import org.example.aispringboot.mapper.KnowledgeArticleMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ArticleReadCountTask {
    //TODO(你)：注入 RedisTemplate<String,Object> 和 KnowledgeArticleMapper
    //   注意：增量是数字，其实用 StringRedisTemplate 更顺（不需要 JSON 序列化）
    //   想一下：用 RedisTemplate<String,Object> 存 Long，写出去会变成什么？
    @Autowired
    private RedisTemplate<String,Object> redis;
    @Autowired
    private KnowledgeArticleMapper articleMapper;

    //仿 FileCleanupTask:23。间隔自己定 —— 先回答：回写太勤 / 太懒，各有什么代价？
    @Scheduled(...)
    public void flushReadCount(){
        //TODO(你) ①：从脏集合取出所有待回写的 articleId
        //TODO(你) ②：逐个：GETDEL 拿到增量（原子地"取回并删除"，别 GET 完再 DEL）
        //TODO(你) ③：增量为空 / 为 0 → 跳过；否则 UPDATE ... read_count = read_count + ?
        //TODO(你) ④：把这个 id 从脏集合里删掉
        //TODO(你) ⑤：想一下 —— ④ 放在 ③ 前面还是后面？
        //   中途抛异常，哪个顺序会造成"漏回写"、哪个会造成"重复回写"？
        //TODO(你) ⑥：整个循环包不包 try-catch？为什么？
    }
}
