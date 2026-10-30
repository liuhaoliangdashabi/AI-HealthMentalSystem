package org.example.common;

import com.auth0.jwt.exceptions.JWTVerificationException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<?>> handleMethodArgumentNotValid(MethodArgumentNotValidException e){
        String message=e.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.isBindingFailure()
                        ? "参数 "+fe.getField()+" 格式不正确"    //类型转换失败，返回人话
                        : fe.getDefaultMessage())               //校验注解的 message，直接用
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
        String detail=String.valueOf(e.getMessage());
        log.warn("唯一索引冲突，data={}",detail);
        if(detail.contains("emotion_diary")||detail.contains("diary_date")){
            return json(Result.error(ResultCode.PARAM_ERROR.getCode(),
                    "今天已经写过日记了，一天只能写一篇",null));
        }
        if(detail.contains("username")||detail.contains("email")){
            return json(Result.error(ResultCode.ACCOUNT_SAME.getCode(),
                    "该用户名或邮箱已被注册，请更换后重试",null));
        }
        return json(Result.error(ResultCode.PARAM_ERROR.getCode(),"数据已存在，请勿重复提交",null));
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

    //POJO 查询参数绑定失败时抛。MethodArgumentNotValidException 是它的子类，上面那个 handler 优先
    @ExceptionHandler(BindException.class)
    public ResponseEntity<Result<?>> handleBind(BindException e){
        String message=e.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.isBindingFailure()
                        ? "参数 "+fe.getField()+" 格式不正确"
                        : String.valueOf(fe.getDefaultMessage()))
                .collect(Collectors.joining(", "));
        log.warn("掉进了参数绑定异常捕获:{}",message);
        return json(Result.error(ResultCode.PARAM_ERROR.getCode(),ResultCode.PARAM_ERROR.getMsg(),message));
    }

    //上传超限时抛。它在进 Controller 之前就抛了，OSSUtil 里的 10MB 校验轮不到执行，两边上限必须一致
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Result<?>> handleMaxUploadSize(MaxUploadSizeExceededException e){
        log.warn("上传文件超过大小限制");
        return json(Result.error(ResultCode.FILE_SIZE_EXCEEDED.getCode(),
                ResultCode.FILE_SIZE_EXCEEDED.getMsg()+"，请压缩后再上传",null));
    }

    //multipart 请求本身格式不对（比如 boundary 缺失、不是 multipart 却硬发文件）
    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<Result<?>> handleMultipart(MultipartException e){
        log.warn("multipart 请求解析失败：{}",e.getMessage());
        return json(Result.error(ResultCode.FILE_UPLOAD_FAILED.getCode(),"文件上传请求格式不正确",null));
    }

    //Content-Type 和服务端要的不一致，比如往 JSON 接口发 form-data
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<Result<?>> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException e){
        log.warn("Content-Type 不支持：收到 {}，支持 {}",e.getContentType(),e.getSupportedMediaTypes());
        return json(Result.error(ResultCode.PARAM_INVALID.getCode(),"请求格式不支持，请检查 Content-Type",null));
    }

    //非唯一键的完整性约束：外键、非空、字段超长等。唯一键冲突被上面的 DuplicateKeyException 接走
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Result<?>> handleDataIntegrity(DataIntegrityViolationException e){
        //不打 e.getMessage()，它经常带着具体的字段值，属于数据外泄
        log.warn("数据库完整性约束冲突，请检查外键 / 非空 / 字段长度限制");
        return json(Result.error(ResultCode.PARAM_ERROR.getCode(),"数据不符合约束，请检查后重试",null));
    }

    //方法级权限校验失败时抛。用 A0300 不用 A0301，前端把 A0301 当成需要重新登录，会把用户踢下线
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Result<?>> handleAccessDenied(AccessDeniedException e){
        log.warn("权限不足：{}",e.getMessage());
        return json(Result.error(ResultCode.AUTHORIZED_ERROR.getCode(),"没有权限执行该操作",null));
    }

    //客户端提前断开时抛。SSE 流式响应期间用户切走就会触发，属正常现象，降成 warn 且不打堆栈
    @ExceptionHandler(AsyncRequestNotUsableException.class)
    public void handleClientDisconnected(AsyncRequestNotUsableException e){
        log.warn("客户端已断开，流式响应中止：{}",e.getMessage());
    }

    //客户端断开时有时直接抛 IOException，而不是被包成 AsyncRequestNotUsableException。
    //消息文案是系统本地化的，靠它判断不可靠，统一降成 warn，但保留精简堆栈
    @ExceptionHandler(IOException.class)
    public void handleIOException(IOException e){
        log.warn("响应写出失败（多半是客户端断开）：\n{}",briefStackTrace(e));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<?>> handleException(Exception e){
        log.error("系统异常：\n{}",briefStackTrace(e));
        return json(Result.error(ResultCode.SYSTEM_ERROR.getCode(), "系统繁忙，请联系管理员", null));
    }


    //堆栈精简：只保留异常名、消息、本项目的调用帧和Caused by链

    private static final String APP_PACKAGE="org.example";
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

    private ResponseEntity<Result<?>> json(Result<?> body){
        return ResponseEntity.status(ResultCode.of(body.getCode()).getHttpStatus())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body);
    }
}
