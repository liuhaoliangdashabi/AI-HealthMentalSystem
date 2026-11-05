package org.example.aispringboot.Test.Redis;

import java.util.concurrent.atomic.AtomicInteger;

public class SimpleCircuitBreaker {
    private int failureThreshold;//连续失败几次就打开
    private long openDurationMs;//打开后等多久
    private final AtomicInteger failures=new AtomicInteger();
    private volatile long openUntil=0;//0->未打开

    public boolean allowRequest(){
        if(openUntil==0)return true;//放行
        if(System.currentTimeMillis()<openUntil)return false;//快速失败
        return true;//放行试探
    }
    public void recordSuccess(){
        failures.set(0);
        openUntil=0;//试探成功，关闭
    }

    public void recordFailure(){
        if(failures.incrementAndGet()>=failureThreshold){
            openUntil=System.currentTimeMillis()+openDurationMs;//打开
        }
    }
}
