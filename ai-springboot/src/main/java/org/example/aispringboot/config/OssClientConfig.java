package org.example.aispringboot.config;

import com.aliyun.sdk.service.oss2.OSSClient;
import com.aliyun.sdk.service.oss2.credentials.EnvironmentVariableCredentialsProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OssClientConfig {

    /*
     * OSS 客户端。官方说明它是线程安全的，全局一个就够。
     * AK/SK 不写进代码，从环境变量读：
     *   OSS_ACCESS_KEY_ID
     *   OSS_ACCESS_KEY_SECRET
     * 生命周期交给 Spring —— 容器关闭时会自动调 close()
     */
    @Bean
    public OSSClient ossClient(OssConfig ossConfig) {
        return OSSClient.newBuilder()
                .credentialsProvider(new EnvironmentVariableCredentialsProvider())
                .region(ossConfig.getRegion())
                .build();
    }
}
