package org.example.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记"这个接口必须有身份才能进"。
 * 生效方式见 IdentityInterceptor：缺 X-User-Id / X-User-Role 时直接抛 401，不进 Controller。
 *
 * adminOnly = true 时额外要求角色是管理员（user_type = 2），否则 403。
 * 后台写接口（建/改/删文章、改状态）用 true；用户能操作自己数据的地方（写日记、删自己的日记）用默认的 false。
 *
 * 只声明在需要身份的接口上，公开接口（文章列表、文章详情、登录、注册）不要加。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireIdentity {

    /** 是否要求管理员角色 */
    boolean adminOnly() default false;
}
