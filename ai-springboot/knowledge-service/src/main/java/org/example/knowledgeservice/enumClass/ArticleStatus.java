package org.example.knowledgeservice.enumClass;

import lombok.Getter;

/**
 * 知识文章状态
 * 取值和前端 knowledge.vue 的按钮显隐逻辑对齐：
 *   0 / 2 → 显示"发布"按钮
 *   1    → 显示"下线"按钮
 */
@Getter
public enum ArticleStatus {

    DRAFT(0, "草稿"),
    PUBLISHED(1, "已发布"),
    OFFLINE(2, "已下线");

    private final Integer code;
    private final String description;

    ArticleStatus(Integer code, String description) {
        this.code = code;
        this.description = description;
    }

    /** 根据代码获取枚举 */
    public static ArticleStatus fromCode(Integer code) {
        for (ArticleStatus status : ArticleStatus.values()) {
            if (status.getCode().equals(code)) {
                return status;
            }
        }
        throw new IllegalArgumentException("未知的文章状态代码: " + code);
    }

    /** 验证文章状态代码是否有效 */
    public static boolean isValidCode(Integer code) {
        for (ArticleStatus status : ArticleStatus.values()) {
            if (status.getCode().equals(code)) {
                return true;
            }
        }
        return false;
    }
}
