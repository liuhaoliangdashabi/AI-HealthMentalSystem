package org.example.diaryservice.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.example.diaryservice.util.RedisKeys;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 日记分析任务的消费者。D8 从 web 搬过来的。
 *
 * 和 web 那个 AiTaskConsumer 的区别只有三点：
 * 1. 用独立 group（diary-workers），不跟 web 抢同一个 PEL
 * 2. 只处理 DIARY_ANALYSIS，收到 EMOTION_ANALYSIS 直接 ACK 丢掉
 * 3. 不做 stream trim（trim 是全局操作，按 key 而不是按 group，
 *    两个服务都 trim 会把对方还没消费的消息删掉，只留 web 那份就够了）
 *
 * 新建 group 用 latest 而不是 0：从 0 会把历史消息全部回放一遍，
 * 那些日记多半早就分析过了或者已经删了，白白刷一屏"日记不存在"。
 */
@Slf4j
@Service
public class AiTaskConsumer {
    @Autowired
    private StringRedisTemplate redis;
    @Autowired
    private DiaryAnalyzer analysisService;

    //组名是逻辑身份（RedisKeys 里固定），实例身份靠这个名字区分
    private static final String consumerName="diary-"+ UUID.randomUUID();
    private static final Duration STALE_SECONDS=Duration.ofSeconds(60);
    private static final int MAX_RETRY=3;

    private static final String TYPE_DIARY="DIARY_ANALYSIS";

    @PostConstruct
    public void init() {
        //内部自带MKSTREAM，stream不存在也会一起建出来，不用提前XADD一条
        try {
            redis.opsForStream().createGroup(RedisKeys.AI_TASK_STREAM,
                    ReadOffset.latest(), RedisKeys.DIARY_TASK_GROUP);
            log.info("消费者组{}就绪，从最新消息开始消费",RedisKeys.DIARY_TASK_GROUP);
        } catch (Exception e) {
            boolean groupExists = false;
            for (Throwable t = e; t != null; t = t.getCause()) {
                if (t.getMessage() != null && t.getMessage().contains("BUSYGROUP")) {
                    groupExists = true;
                    break;
                }
            }
            if (!groupExists) {
                log.error("创建消费者组{}失败，日记分析不会执行",RedisKeys.DIARY_TASK_GROUP, e);
            }
        }
    }

    //read 的 StreamOffset 是泛型可变参数，Java 不能创建泛型数组，这里类型确定，安全压制 unchecked
    /** 主循环：读新消息。fixedDelay 800ms 是排队延迟和空转之间的折中，真正的耗时在模型调用。 */
    @SuppressWarnings("unchecked")
    @Scheduled(fixedDelay=800)
    public void consumeAiTask(){
        try {
            List<MapRecord<String,Object,Object>> records=
                    redis.opsForStream().read(
                          Consumer.from(RedisKeys.DIARY_TASK_GROUP,consumerName),
                          StreamReadOptions.empty().count(10),
                          StreamOffset.create(RedisKeys.AI_TASK_STREAM,ReadOffset.lastConsumed())
                    );
            if(records==null)return;
            handleRecord(records);
        } catch (Exception e) {
            if(isNoGroup(e)){
                log.warn("消费者组不存在(Redis可能被清空)，重建组{}",RedisKeys.DIARY_TASK_GROUP);
                try{
                    redis.opsForStream().createGroup(RedisKeys.AI_TASK_STREAM,
                            ReadOffset.latest(),RedisKeys.DIARY_TASK_GROUP);
                }catch(Exception ignore){}
            }else{
                log.warn("读取AI任务流失败，本轮跳过",e);
            }
        }
    }

    @Scheduled(fixedDelay = 30000)
    public void cleanStaleTask(){
        PendingMessages pending=redis.opsForStream().pending(
                RedisKeys.AI_TASK_STREAM, RedisKeys.DIARY_TASK_GROUP,
                Range.unbounded(), 10);
        //这是无条件new的，不会返回null
        if( pending.isEmpty())return;
        for(PendingMessage pm:pending){
            long deliveries=pm.getTotalDeliveryCount();
            Duration idle=pm.getElapsedTimeSinceLastDelivery();
            if(deliveries>=MAX_RETRY){
                try {
                    //投太多次，转死信，不再重投（停止重试，把消息连同失败原因一起存档）
                    redis.opsForStream().add(RedisKeys.AI_TASK_DEAD_STREAM, Map.of(
                            "originalId", pm.getIdAsString(),
                            "deliveries", String.valueOf(deliveries),
                            "source", "diary-service",
                            "reason","重试次数达上限"));
                    redis.opsForStream().acknowledge(RedisKeys.AI_TASK_STREAM, RedisKeys.DIARY_TASK_GROUP, pm.getId());
                    log.error("任务重试{}次仍失败，转死信，id={}",deliveries,pm.getIdAsString());
                } catch (Exception e) {
                    log.error("转死信失败，下一轮重试，id={}",pm.getIdAsString(),e);
                }
                continue;
            }

            if(idle.compareTo(STALE_SECONDS)<=0){
                //没到闲置阈值，消费者可能还在跑，下一轮看
                continue;
            }

            List<MapRecord<String,Object,Object>> claimed=redis.opsForStream().claim(
                    RedisKeys.AI_TASK_STREAM,RedisKeys.DIARY_TASK_GROUP,
                    consumerName,STALE_SECONDS,pm.getId());
            //内层返回List<ByteRecord>，如果null，还有第二层返回emptyList，永远不会为null
            if(claimed.isEmpty())continue;//被别人抢先了
            handleRecord(claimed);
        }
    }

    /**
     * 正常消费与 claim 接管共用的处理函数。
     *
     * 只认 DIARY_ANALYSIS。其他 type 一律 ACK 丢掉 —— 必须先 ACK，
     * 否则它们会一直挂在 8030 的 PEL 里，30 秒后被 claim 重试 3 次再进死信，
     * 把死信流刷成垃圾场，真出问题的时候根本看不出哪条是有意义的。
     * 空 type 的历史消息同理：那是会话分析，归 web 管。
     */
    private void handleRecord(List<MapRecord<String,Object,Object>> records) {
        for (MapRecord<String, Object, Object> record : records) {
            String type = readString(record, "type");

            if (!TYPE_DIARY.equals(type)) {
                //不是我的活，签收走人。debug 级别，否则每 800ms 就会刷一行
                log.debug("跳过非日记任务，type={}，recordId={}", type, record.getId());
                acknowledge(record, "跳过 type=" + type);
                continue;
            }

            Long diaryId = readLong(record, "diaryId");
            if (diaryId == null) {
                discardPoison(record, "diaryId 缺失或不是数字");
                continue;
            }

            boolean ok;
            try {
                ok = analysisService.analyzeDiary(diaryId);
            } catch (Exception e) {
                //瞬时失败（模型限流、DB 抖动）→ 不签收，留给 PEL 兜底重投
                log.warn("处理日记任务失败，留给PEL兜底，recordId={}，diaryId={}", record.getId(), diaryId, e);
                continue;
            }

            if (ok) {
                acknowledge(record, "diaryId=" + diaryId);
            }
        }
    }

    /** 取字符串字段。不存在返回 null 而不是空串，调用方要能区分这两种情况。 */
    private String readString(MapRecord<String, Object, Object> record, String field) {
        Object raw = record.getValue().get(field);
        return raw == null ? null : raw.toString();
    }

    /** 取数字字段。先判 null 再解析，null 调 toString 抛的是 NPE，catch 不住。 */
    private Long readLong(MapRecord<String, Object, Object> record, String field) {
        Object raw = record.getValue().get(field);
        if (raw == null) {
            return null;
        }
        try {
            return Long.parseLong(raw.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 毒消息：格式坏的，重投多少次都还是坏的，只能签收丢弃，否则永远卡在 PEL 里。 */
    private void discardPoison(MapRecord<String, Object, Object> record, String reason) {
        log.error("任务消息格式错误（{}），丢弃，recordId={}，内容={}", reason, record.getId(), record.getValue());
        try {
            redis.opsForStream().acknowledge(RedisKeys.AI_TASK_STREAM, RedisKeys.DIARY_TASK_GROUP, record.getId());
        } catch (Exception e) {
            log.warn("毒消息签收失败，recordId={}", record.getId(), e);
        }
    }

    private void acknowledge(MapRecord<String, Object, Object> record, String desc) {
        try {
            redis.opsForStream().acknowledge(RedisKeys.AI_TASK_STREAM, RedisKeys.DIARY_TASK_GROUP, record.getId());
        } catch (Exception e) {
            log.warn("{}消息已处理，但签收失败，留在PEL被重投", desc, e);
        }
    }

    private boolean isNoGroup(Throwable e){
        for(Throwable t=e;t!=null;t=t.getCause()){
            if(t.getMessage()!=null && t.getMessage().contains("NOGROUP"))return true;
        }
        return false;
    }
}
