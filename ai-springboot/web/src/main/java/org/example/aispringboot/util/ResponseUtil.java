package org.example.aispringboot.util;

import cn.hutool.core.exceptions.ExceptionUtil;
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
        //状态码统一由 ResultCode 提供，不再在这里维护第二份映射
        response.setStatus(resultCode.getHttpStatus());
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
