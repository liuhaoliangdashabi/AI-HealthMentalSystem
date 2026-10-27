package org.example.common;

import lombok.Getter;

/** 业务异常。外部不需要修改属性，所以只提供 getter。 */
//外部不需要修改属性，所以只包含getter方法即可
@Getter
public class BusinessException extends RuntimeException {

    private final String code;
    private final String message;
    private final Object data;

    public BusinessException(String message) {
        super(message);
        this.code = ResultCode.BUSINESS_ERROR.getCode();
        this.message = message;
        this.data = null;
    }

    //带附加数据的业务异常：额外信息会放进 Result.data，前端可以拿去做具体处理
    public BusinessException(String message, Object data) {
        super(message);
        this.code = ResultCode.BUSINESS_ERROR.getCode();
        this.message = message;
        this.data = data;
    }

    //自定义错误码 + 附加数据
    public BusinessException(String code, String message, Object data) {
        super(message);
        this.code = code;
        this.message = message;
        this.data = data;
    }

}