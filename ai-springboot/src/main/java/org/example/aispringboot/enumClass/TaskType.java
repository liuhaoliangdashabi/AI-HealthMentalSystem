package org.example.aispringboot.enumClass;

import lombok.Getter;

/**
 * AI分析任务的触发类型
 * 注意：数据库存的是 varchar，所以 code 是字符串，不是数字
 */
@Getter
public enum TaskType {

    AUTO("AUTO", "自动触发"),
    MANUAL("MANUAL", "手动触发"),
    ADMIN("ADMIN", "管理员触发"),
    BATCH("BATCH", "批量触发");

    private final String code;
    private final String description;

    TaskType(String code, String description) {
        this.code = code;
        this.description = description;
    }

    /** 根据代码获取枚举 */
    public static TaskType fromCode(String code) {
        for (TaskType type : TaskType.values()) {
            if (type.getCode().equals(code)) {
                return type;
            }
        }
        throw new IllegalArgumentException("未知的任务类型: " + code);
    }

    /** 验证任务类型代码是否有效 */
    public static boolean isValidCode(String code) {
        for (TaskType type : TaskType.values()) {
            if (type.getCode().equals(code)) {
                return true;
            }
        }
        return false;
    }
}
