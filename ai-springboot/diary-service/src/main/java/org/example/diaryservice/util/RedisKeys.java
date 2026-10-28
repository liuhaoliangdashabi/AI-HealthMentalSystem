package org.example.diaryservice.util;

import java.time.Duration;
import java.time.LocalDate;

public class RedisKeys {
    private RedisKeys(){}

    public static final Duration UV_TTL=Duration.ofDays(40);

    public static String uv(LocalDate day) {
        return "uv:" + day;
    }

    //这天查过库，得到的结果是0
    public static String uvEmpty(LocalDate day) {
        return "uv:empty:" + day;
    }

    //========== AI 任务队列（Stream）==========

    //完整 key，末尾不加冒号
    public static final String AI_TASK_STREAM="stream:ai:task";

    //重试超过上限的任务转到死信，人工排查用
    public static final String AI_TASK_DEAD_STREAM="stream:ai:task:dead";

    //消费者组名：逻辑身份，永远不变。实例身份用 consumerName 区分
    public static final String AI_TASK_GROUP="ai-workers";

    //========== AI 情绪分析结果缓存（String + JSON）==========

    //这是前缀不是完整 key，末尾必须有冒号，后面要拼 md5
    //漏了冒号不会报错，只会让 KEYS ai:emotion:analysis:* 搜不到
    public static final String AI_EMOTION_CACHE_PREFIX="ai:emotion:analysis:";

    //========== 文章热度（ZSet）==========
    //ArticleReadCountTask 写、KnowledgeArticleService 读，两个文件都要拼，必须收敛

    //天级 key 前缀，末尾必须有冒号，后面拼 LocalDate
    public static final String ARTICLE_HOT_DAY_PREFIX="article:hot:";

    //窗口天数：预热和查询都引用它，改窗口只改这一处
    //必须声明在 ARTICLE_HOT_WINDOW 之前，static final 按书写顺序初始化，反过来写会报 illegal forward reference
    public static final int ARTICLE_HOT_WINDOW_DAYS=7;

    //最近 N 天的合并结果，定时 ZUNIONSTORE 预热
    //拼上天数，改窗口时 key 自动跟着变，避免名字和内容对不上
    public static final String ARTICLE_HOT_WINDOW="article:hot:"+ARTICLE_HOT_WINDOW_DAYS+"d";

    //合并结果的 TTL，要比预热间隔(30秒)长；但不能太长，否则任务停了前端还在看旧榜
    public static final Duration ARTICLE_HOT_WINDOW_TTL=Duration.ofSeconds(90);

    //单篇文章浏览增量、待回写的脏集合
    public static final String ARTICLE_READ_DELTA_PREFIX="article:read:delta:";
    public static final String ARTICLE_READ_DIRTY="article:read:dirty";

    //========== 在线用户（ZSet 心跳）==========

    //完整 key。member = userId，score = 最后一次活跃的毫秒时间戳
    //用 ZSet 不用 Set：Set 只能回答"谁来过"，要判断"还在不在"必须按时间过滤
    public static final String ONLINE_USERS="online:users";

    //超过这么久没有请求，就认为已离线。前端没有心跳接口，只能靠"访问页面"续命
    public static final Duration ONLINE_TIMEOUT=Duration.ofMinutes(5);
}
