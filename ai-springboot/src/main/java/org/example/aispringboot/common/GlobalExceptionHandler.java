package org.example.aispringboot.common;

import com.auth0.jwt.exceptions.JWTVerificationException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.example.aispringboot.exception.BusinessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private ResponseEntity<Result<?>> json(Result<?> body){
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(body);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<?>> handleMethodArgumentNotValid(MethodArgumentNotValidException e){
        String message=e.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.isBindingFailure()
                        ? "参数 "+fe.getField()+" 格式不正确"    // 类型转换 → 天书，换人话
                        : fe.getDefaultMessage())               // 校验注解 → 你写的，直接用
                .collect(Collectors.joining(", "));
        log.warn("掉进了参数校验异常捕获:{}",message);
        return json(Result.error(ResultCode.PARAM_ERROR.getCode(),ResultCode.PARAM_ERROR.getMsg(),message));
    }


    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Result<?>> handleConstraintViolation(ConstraintViolationException e){
        String message=e.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.joining(", "));
        log.warn("掉进了参数校验异常捕获（路径/查询参数）:{}",message);
        return json(Result.error(ResultCode.PARAM_ERROR.getCode(),ResultCode.PARAM_ERROR.getMsg(),message));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Result<?>> handleNotReadable(HttpMessageNotReadableException e){
        //e.getMessage() 会原样带出解析失败处的文本（实测：password 忘加引号 → 明文进日志），所以不打
        log.warn("掉进了请求体解析异常捕获，多半是 JSON 格式错或 body 不是 JSON");
        return json(Result.error(ResultCode.PARAM_INVALID.getCode(),"请求体格式不正确",null));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Result<?>> handleMissingParam(MissingServletRequestParameterException e){
        log.warn("掉进了缺参数异常捕获，缺少的参数是:{}",e.getParameterName());
        return json(Result.error(ResultCode.PARAM_MISSING.getCode(),
                ResultCode.PARAM_MISSING.getMsg()+"："+e.getParameterName(),null));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Result<?>> handleTypeMismatch(MethodArgumentTypeMismatchException e){
        log.warn("掉进了参数类型异常捕获，参数名={}，传进来的值={}",e.getName(),e.getValue());
        return json(Result.error(ResultCode.PARAM_INVALID.getCode(),
                "参数 "+e.getName()+" 类型不正确",null));
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<?>> handleBusinessException(BusinessException e){
        log.warn("掉进了business异常捕获，多半是业务有问题:{}",e.getMessage());
        if(e.getData()!=null){
            return json(Result.error(e.getCode(),e.getMessage(),e.getData()));
        }else{
            return json(Result.error(e.getCode(),e.getMessage(),null));
        }
    }

    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<Result<?>> handleDuplicateKey(DuplicateKeyException e) {
        log.warn("唯一索引冲突，data={}", e.getMessage());
        return json(Result.error(ResultCode.ACCOUNT_SAME.getCode(), "该用户名或邮箱已被注册，请更换后重试", null));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Result<?>> handleMethodNotSupported(HttpRequestMethodNotSupportedException e){
        log.warn("请求方法不支持，方法是:{}",e.getMethod());
        return json(Result.error(ResultCode.PARAM_ERROR.getCode(),"请求方法不支持："+e.getMethod(),null));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Result<?>> handleNotFound(NoResourceFoundException e) {
        log.warn("接口不存在：{}", e.getResourcePath());
        return json(Result.error(ResultCode.NOT_FOUND.getCode(), "接口不存在", null));
    }

    @ExceptionHandler(JWTVerificationException.class)
    public ResponseEntity<Result<?>> handleJwtVerification(JWTVerificationException e){
        log.warn("掉进了token校验异常捕获，token无效或已过期:{}",e.getMessage());
        return json(Result.error(ResultCode.TOKEN_INVALID.getCode(),ResultCode.TOKEN_INVALID.getMsg(),null));
    }

    /**
     * 客户端提前断开时抛的。
     * SSE 流式响应期间，用户切会话/刷新/关标签页，服务端还在往一个已经断掉的连接里写，就会抛这个。
     * 这是【正常现象】，不是服务端故障 —— 所以降成 warn 且不打堆栈，否则一刷屏就把真问题淹了。
     */
    @ExceptionHandler(AsyncRequestNotUsableException.class)
    public void handleClientDisconnected(AsyncRequestNotUsableException e){
        log.warn("客户端已断开，流式响应中止：{}",e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<?>> handleException(Exception e){
        log.error("系统异常：\n{}",briefStackTrace(e));
        return json(Result.error(ResultCode.SYSTEM_ERROR.getCode(), "系统繁忙，请联系管理员", null));
    }

    //==================================== 堆栈精简 ====================================
    //e.printStackTrace() 动辄几百行，九成是框架内部调用，真正的出错点反而被埋了。
    //这里只保留：异常名 + 消息 + 【我们自己的包】的调用帧 + Caused by 链。

    private static final String APP_PACKAGE="org.example.aispringboot";
    private static final int MAX_CAUSE_DEPTH=5;   //最多跟几层 Caused by
    private static final int MAX_FRAMES=8;        //每层最多打几帧

    private String briefStackTrace(Throwable e){
        StringBuilder sb=new StringBuilder();
        Throwable current=e;
        for(int depth=0; current!=null && depth<MAX_CAUSE_DEPTH; depth++){
            sb.append(depth==0?"":"\n  Caused by: ")
                    .append(current.getClass().getSimpleName());
            if(current.getMessage()!=null){
                sb.append(": ").append(current.getMessage());
            }

            List<StackTraceElement> frames=Arrays.stream(current.getStackTrace())
                    .filter(f -> f.getClassName().startsWith(APP_PACKAGE))
                    .limit(MAX_FRAMES)
                    .toList();

            //整个异常都发生在框架里（比如 SQL 语法错），那就退而求其次给前 3 帧，不然一片空白
            if(frames.isEmpty()){
                frames=Arrays.stream(current.getStackTrace()).limit(3).toList();
            }
            frames.forEach(f -> sb.append("\n      at ").append(f));

            current=current.getCause();
        }
        return sb.toString();
    }
}
