package org.example.consultservice.util;

/**
 * 本模块用到的 RocketMQ 常量。
 *
 * 只放本模块自己的 tag，不往 common 里塞 —— 三个服务各建一个类，
 * 避免改一个 tag 影响全部服务。
 *
 * ⚠️ tag 严格区分大小写。写错（DIARY 写成 Diary）不会报错，
 * 消息会被静默丢弃：offset 正常前进，onMessage 一次都不执行，也不进死信。
 */
public class RocketMQUtil {
    private RocketMQUtil(){}

    /** 对话情绪分析任务。topic 统一是 ai-task，靠 tag 分流 */
    public static final String TAG_EMOTION="EMOTION";
}
