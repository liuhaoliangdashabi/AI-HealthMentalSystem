package org.example.aispringboot.util;


import java.time.Duration;
import java.time.LocalDate;

public class RedisKeys {
    private RedisKeys(){}

    public static final Duration UV_TTL=Duration.ofDays(40);

    public static String uv(LocalDate day) {
        return "uv:" + day;
    }

    //这天查过库，得到的结果是0
    public static String uvEmpty(LocalDate day) {
        return "uv:empty:" + day;
    }
}
