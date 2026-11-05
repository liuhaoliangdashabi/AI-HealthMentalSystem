package org.example.aispringboot.enumClass;

import lombok.Getter;

/**
 * AI分析任务优先级
 * 数据库默认值是 2（正常）
 */
@Getter
public enum TaskPriority {

    LOW(1, "低"),
    NORMAL(2, "正常"),
    HIGH(3, "高"),
    URGENT(4, "紧急");

    private final Integer code;
    private final String description;

    TaskPriority(Integer code, String description) {
        this.code = code;
        this.description = description;
    }

    /** 根据代码获取枚举 */
    public static TaskPriority fromCode(Integer code) {
        for (TaskPriority priority : TaskPriority.values()) {
            if (priority.getCode().equals(code)) {
                return priority;
            }
        }
        throw new IllegalArgumentException("未知的任务优先级: " + code);
    }

    /** 验证任务优先级代码是否有效 */
    public static boolean isValidCode(Integer code) {
        for (TaskPriority priority : TaskPriority.values()) {
            if (priority.getCode().equals(code)) {
                return true;
            }
        }
        return false;
    }
}
