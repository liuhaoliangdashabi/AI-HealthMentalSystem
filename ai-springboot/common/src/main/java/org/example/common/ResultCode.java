package org.example.common;

import lombok.Data;
import lombok.Getter;

import java.util.Arrays;

@Getter
public enum ResultCode {
    SUCCESS("200", "操作成功",200),
    ERROR("-1", "操作失败",500),
    UNAUTHORIZED("401", "暂未登录或token已经过期",401),
    SYSTEM_ERROR("500", "系统错误",500),

    // 参数相关错误
    PARAM_ERROR("400", "参数错误",400),
    PARAM_MISSING("4001", "缺少必要参数",400),
    PARAM_INVALID("4002", "参数格式不正确",400),

    // 文件操作相关错误
    FILE_NOT_FOUND("5001", "文件不存在",400),
    FILE_UPLOAD_FAILED("5002", "文件上传失败",400),
    FILE_DELETE_FAILED("5003", "文件删除失败",400),
    FILE_SIZE_EXCEEDED("5004", "文件大小超过限制",413),
    FILE_TYPE_NOT_SUPPORTED("5005", "不支持的文件类型",400),
    FILE_NAME_INVALID("5006", "文件名不合法",400),
    FILE_CONTENT_INVALID("5007", "文件内容不合法",400),
    FILE_SAVE_FAILED("5008", "文件保存失败",400),

    // 业务相关错误
    BUSINESS_ERROR("6000", "业务处理失败",200),
    ACCOUNT_SAME("6001", "用户名已存在",200),
    USER_NOT_EXIST("6002", "用户不存在",200),

    // token相关错误
    TOKEN_INVALID("A0230", "token无效",401),
    TOKEN_EXPIRED("A0230", "token已过期",401),
    TOKEN_BLOCKED("A0230", "token已加入黑名单",401),
    TOKEN_ACCESS_FORBIDDEN("A0231", "token已被禁止访问",403),
    AUTHORIZED_ERROR("A0300", "访问权限异常",403),
    ACCESS_UNAUTHORIZED("A0301", "访问未授权",401),

    //路径相关错误——
    NOT_FOUND("B0120","路径错误",404);

    private final String code;
    private final String msg;
    private final int httpStatus;

    ResultCode(String code, String msg,int httpStatus) {
        this.code = code;
        this.msg = msg;
        this.httpStatus=httpStatus;
    }

    public static ResultCode of(String code){
        return Arrays.stream(values())
                .filter(r->r.code.equals(code))
                .findFirst()
                .orElse(BUSINESS_ERROR);
    }

}
