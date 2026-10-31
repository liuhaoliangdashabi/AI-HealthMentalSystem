package org.example.common.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.example.common.BusinessException;
import org.example.common.ResultCode;
import org.example.common.annotation.RequireIdentity;
import org.example.common.util.IdentityUtil;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 只拦挂了 {@link RequireIdentity} 的方法。
 * 没挂注解的（公开接口）直接放行，所以不会误伤登录、注册、文章列表。
 *
 * 为什么不抛 ResponseEntity 而是抛 BusinessException：
 * 交给 GlobalExceptionHandler 统一包成 Result，错误体格式和其它异常保持一致，不会一个接口两种 JSON。
 */
@Slf4j
public class IdentityInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod method)) {
            return true;
        }
        RequireIdentity anno = method.getMethodAnnotation(RequireIdentity.class);
        if (anno == null) {
            return true;
        }

        String userId = request.getHeader(IdentityUtil.HEADER_USER_ID);
        String role = request.getHeader(IdentityUtil.HEADER_USER_ROLE);
        if (userId == null || userId.isBlank() || role == null || role.isBlank()) {
            //能走到这里说明调用方没经过网关（或者网关白名单漏配），属配置问题：warn，不落 error
            log.warn("缺少用户身份请求头，拒绝访问：{} {}，X-User-Id={}，X-User-Role={}",
                    request.getMethod(), request.getRequestURI(), userId, role);
            throw new BusinessException(ResultCode.UNAUTHORIZED.getCode(), "缺少用户身份信息，请重新登录", null);
        }

        Integer roleType;
        try {
            roleType = Integer.valueOf(role.trim());
        } catch (NumberFormatException e) {
            log.warn("身份头格式非法，拒绝访问：{} {}，X-User-Role={}",
                    request.getMethod(), request.getRequestURI(), role);
            throw new BusinessException(ResultCode.UNAUTHORIZED.getCode(), "用户身份信息非法，请重新登录", null);
        }

        if (anno.adminOnly() && !IdentityUtil.isAdmin(roleType)) {
            //登录了但没权限，是安全事件：warn 但记全上下文，方便追人
            log.warn("越权访问后台接口，已拦截：{} {}，X-User-Id={}，X-User-Role={}",
                    request.getMethod(), request.getRequestURI(), userId, roleType);
            throw new BusinessException(ResultCode.AUTHORIZED_ERROR.getCode(), "无权访问该接口", null);
        }
        return true;
    }
}
