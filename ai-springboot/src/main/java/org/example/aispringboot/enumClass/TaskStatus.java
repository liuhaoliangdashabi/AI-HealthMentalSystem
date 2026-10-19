package org.example.aispringboot.enumClass;

import lombok.Getter;

/**
 * AI分析任务状态
 * 注意：数据库存的是 varchar，所以 code 是字符串，不是数字
 */
@Getter
public enum TaskStatus {

    PENDING("PENDING", "待处理"),
    PROCESSING("PROCESSING", "处理中"),
    COMPLETED("COMPLETED", "已完成"),
    FAILED("FAILED", "失败");

    private final String code;
    private final String description;

    TaskStatus(String code, String description) {
        this.code = code;
        this.description = description;
    }

    /** 根据代码获取枚举 */
    public static TaskStatus fromCode(String code) {
        for (TaskStatus status : TaskStatus.values()) {
            if (status.getCode().equals(code)) {
                return status;
            }
        }
        throw new IllegalArgumentException("未知的任务状态: " + code);
    }

    /** 验证任务状态代码是否有效 */
    public static boolean isValidCode(String code) {
        for (TaskStatus status : TaskStatus.values()) {
            if (status.getCode().equals(code)) {
                return true;
            }
        }
        return false;
    }
}
