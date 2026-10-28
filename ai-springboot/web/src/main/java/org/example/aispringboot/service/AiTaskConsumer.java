package org.example.aispringboot.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.example.aispringboot.AiService.EmotionAnalysisService;
import org.example.aispringboot.util.RedisKeys;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.cache.CacheProperties;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
@Slf4j
@Service
public class AiTaskConsumer {
    @Autowired
    private StringRedisTemplate redis;
    @Autowired
    private EmotionAnalysisService analysisService;

    //组名是逻辑身份（RedisKeys 里固定），实例身份靠这个名字区分
    private static final String consumerName="work-"+ UUID.randomUUID();
    private static final Duration STALE_SECONDS=Duration.ofSeconds(60);
    private static final int MAX_RETRY=3;

    @PostConstruct
    public void init() {
        //内部自带MKSTREAM，stream不存在也会一起建出来，不用提前XADD一条
        try {
            redis.opsForStream().createGroup(RedisKeys.AI_TASK_STREAM, ReadOffset.from("0"),RedisKeys.AI_TASK_GROUP);
        } catch (Exception e) {
            boolean groupExists = false;
            for (Throwable t = e; t != null; t = t.getCause()) {
                if (t.getMessage() != null && t.getMessage().contains("BUSYGROUP")) {
                    groupExists = true;
                    break;
                }
            }
            if (!groupExists) {
                log.error("创建消费者组失败，消费者可能无法工作", e);
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
                          Consumer.from(RedisKeys.AI_TASK_GROUP,consumerName),
                          StreamReadOptions.empty().count(10),
                          StreamOffset.create(RedisKeys.AI_TASK_STREAM,ReadOffset.lastConsumed())
                    );
            if(records==null)return;
            handleRecord(records);
        } catch (Exception e) {
            if(isNoGroup(e)){
                log.warn("消费者组不存在(Redis可能被清空)，重建组{}",RedisKeys.AI_TASK_GROUP);
                try{
                    redis.opsForStream().createGroup(RedisKeys.AI_TASK_STREAM,
                            ReadOffset.from("0"),RedisKeys.AI_TASK_GROUP);
                }catch(Exception ignore){}
            }else{
                log.warn("读取AI任务流失败，本轮跳过",e);
            }
        }
    }

    @Scheduled(fixedDelay = 30000)
    public void cleanStaleTask(){
        PendingMessages pending=redis.opsForStream().pending(
                RedisKeys.AI_TASK_STREAM, RedisKeys.AI_TASK_GROUP,
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
                            "reason","重试次数达上限"));
                    redis.opsForStream().acknowledge(RedisKeys.AI_TASK_STREAM, RedisKeys.AI_TASK_GROUP, pm.getId());
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
                    RedisKeys.AI_TASK_STREAM,RedisKeys.AI_TASK_GROUP,
                    consumerName,STALE_SECONDS,pm.getId());
            //内层返回List<ByteRecord>，如果null，还有第二层返回emptyList，永远不会为null
            if(claimed.isEmpty())continue;//被别人抢先了
            handleRecord(claimed);
        }
    }

    @Scheduled(cron = "0 0 */1 * * *")
    public void trimStream(){
        try{
            Long removed=redis.opsForStream().trim(RedisKeys.AI_TASK_STREAM,1000,true);
            if(removed!=null && removed>0)log.info("裁剪AI任务流，移除{}条历史消息",removed);
        }catch(Exception e){
            log.warn("近似裁剪过期任务失败",e);
        }
    }

    /** 队列指标，给管理端看。读失败返回 -1 而不是 0，避免和"真的没有积压"混淆。 */
    public Map<String,Object> stats(){
        Map<String,Object> m=new LinkedHashMap<>();
        Long len= null;
        try {
            len = redis.opsForStream().size(RedisKeys.AI_TASK_STREAM);
        } catch (Exception e) {
            log.warn("读取AI任务stream流长度失败",e);
        }
        m.put("streamLength",len==null?-1L:len);

        try {
            PendingMessagesSummary sum=redis.opsForStream().pending(
                    RedisKeys.AI_TASK_STREAM,RedisKeys.AI_TASK_GROUP);
            m.put("pending",sum.getTotalPendingMessages());
            m.put("pendingPerConsumer",sum.getPendingMessagesPerConsumer());
        } catch (Exception e) {
            log.warn("读取AI任务pending信息失败",e);
            m.put("pending",-1L);
            m.put("pendingPerConsumer",Map.of());
        }

        Long dead= null;
        try {
            dead = redis.opsForStream().size(RedisKeys.AI_TASK_DEAD_STREAM);
        } catch (Exception e) {
            log.warn("读取AI任务死信流长度失败",e);
        }
        m.put("deadLetters",dead==null?-1L:dead);

        //当前实例的消费者名，排查"到底是谁卡住了"时有用
        m.put("consumer",consumerName);
        return m;
    }


    /**
     * 正常消费与 claim 接管共用的处理函数，按 type 分流。
     * type 是后加的字段，历史消息没有它，默认按 EMOTION_ANALYSIS 处理，不能当毒消息丢弃。
     */
    private void handleRecord(List<MapRecord<String,Object,Object>> records) {

        for (MapRecord<String, Object, Object> record : records) {
            String type = readString(record, "type");
            if (type == null || type.isBlank()) {
                type = "EMOTION_ANALYSIS";   //兼容 type 字段上线前的历史消息
            }

            boolean ok;
            String desc;
            try {
                switch (type) {
                    case "DIARY_ANALYSIS" -> {
                        Long diaryId = readLong(record, "diaryId");
                        if (diaryId == null) {
                            discardPoison(record, "diaryId 缺失或不是数字");
                            continue;
                        }
                        ok = analysisService.analyzeDiary(diaryId);
                        desc = "diaryId=" + diaryId;
                    }
                    case "EMOTION_ANALYSIS" -> {
                        Long sessionId = readLong(record, "sessionId");
                        if (sessionId == null) {
                            discardPoison(record, "sessionId 缺失或不是数字");
                            continue;
                        }
                        ok = analysisService.analyze(sessionId);
                        desc = "sessionId=" + sessionId;
                    }
                    default -> {
                        //真·未知类型：可能是新版本投的、老版本在读，重投也没用
                        discardPoison(record, "未知任务类型 " + type);
                        continue;
                    }
                }
            } catch (Exception e) {
                //瞬时失败（模型限流、DB 抖动）→ 不签收，留给 PEL 兜底重投
                log.warn("处理任务失败，留给PEL兜底，recordId={}，type={}", record.getId(), type, e);
                continue;
            }

            if (ok) {
                acknowledge(record, desc);
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
            redis.opsForStream().acknowledge(RedisKeys.AI_TASK_STREAM, RedisKeys.AI_TASK_GROUP, record.getId());
        } catch (Exception e) {
            log.warn("毒消息签收失败，recordId={}", record.getId(), e);
        }
    }

    private void acknowledge(MapRecord<String, Object, Object> record, String desc) {
        try {
            redis.opsForStream().acknowledge(RedisKeys.AI_TASK_STREAM, RedisKeys.AI_TASK_GROUP, record.getId());
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
