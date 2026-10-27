package org.example.aispringboot.util;

import cn.hutool.core.exceptions.ExceptionUtil;
import cn.hutool.http.HttpStatus;
import cn.hutool.json.JSONUtil;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.example.common.Result;
import org.example.common.ResultCode;
import org.springframework.http.MediaType;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
@Slf4j
public class ResponseUtil {
    //处理过滤器中的异常响应——在Controller之前，Result并不适用
    public static void WriteError(HttpServletResponse response, ResultCode resultCode){
        //不同状态码返回不同响应
        int status=switch(resultCode){
            //暂未登录、访问未授权、token不合法、token过期、token被加入黑名单 -> 401
            case UNAUTHORIZED,ACCESS_UNAUTHORIZED,TOKEN_INVALID,TOKEN_EXPIRED,TOKEN_BLOCKED->
                    HttpStatus.HTTP_UNAUTHORIZED;
            //token被禁止访问 -> 403
            case TOKEN_ACCESS_FORBIDDEN -> HttpStatus.HTTP_FORBIDDEN;
            default -> HttpStatus.HTTP_BAD_REQUEST;
        };
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());

        try(PrintWriter writer=response.getWriter()){
            String jsonResponse=JSONUtil.toJsonStr(Result.error(resultCode.getCode(),resultCode.getMsg(),null));
            writer.print(jsonResponse);
            writer.flush();//相应内容写入到输出流中
        }catch(IOException e){
            log.warn("写入响应失败：{}", ExceptionUtil.getRootCauseMessage(e));
        }
    }

}
