package org.example.knowledgeservice.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 分页插件
 * 缺了它mybatisplus的selectPage不会报错，静默降级
 * SQL不带limit全表返回，page.getTotal() 停在默认值 0。
 * 所以前端看到的是一页塞满所有数据 + 总数 0 + 页码算不出来。
 * D3 复制配置类时漏了这个（只搬了 RedisConfig / CacheConfig），D5 验收时才发现。
 */
@Configuration
public class MpConfig {
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }
}
