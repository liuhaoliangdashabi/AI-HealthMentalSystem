package org.example.common.util;

/**
 * 身份相关的取值与判断，三个服务共用。
 * 网关注入的请求头名只在这里定义一次，改名字不用全项目搜。
 */
public final class IdentityUtil {

    public static final String HEADER_USER_ID = "X-User-Id";
    public static final String HEADER_USER_ROLE = "X-User-Role";
    public static final String HEADER_USER_NAME = "X-User-Name";

    /** 管理员角色值，和 user.user_type 对齐 */
    public static final int ROLE_ADMIN = 2;

    private IdentityUtil() {
    }

    /**
     * 是不是管理员。
     * 常量在前：roleType 为 null 时返回 false，落到"非管理员"，不会 NPE。
     * 反过来写 roleType == ROLE_ADMIN 遇到 null 会拆箱失败。
     */
    public static boolean isAdmin(Integer roleType) {
        return Integer.valueOf(ROLE_ADMIN).equals(roleType);
    }
}
