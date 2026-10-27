package org.example.common;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Result<T> {
    private String code;
    private T data;
    private String msg;

    public static <T> Result<T> success(){
        Result<T> result=new Result<>();
        result.setCode(ResultCode.SUCCESS.getCode());
        result.setMsg(ResultCode.SUCCESS.getMsg());
        return result;
    }

    public static <T> Result<T> success(T data){
        Result<T> result=success();
        result.setData(data);
        return result;
    }

    public static <T> Result<T> error(){
        Result<T> result=new Result<>();
        result.setCode(ResultCode.ERROR.getCode());
        result.setMsg(ResultCode.ERROR.getMsg());
        return result;
    }

    public static <T> Result<T> error(String code,String msg,T data){
        Result<T> result=new Result<>();
        result.setCode(code);
        result.setMsg(msg);
        result.setData(data);
        return result;
    }
}
