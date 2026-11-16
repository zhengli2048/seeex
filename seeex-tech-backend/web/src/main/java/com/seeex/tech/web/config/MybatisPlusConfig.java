package com.seeex.tech.web.config;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
@Configuration @MapperScan({"com.seeex.tech.web.onboarding", "com.seeex.tech.web.governance", "com.seeex.tech.web.organization.mapper", "com.seeex.tech.web.registry.mapper", "com.seeex.tech.web.rbac", "com.seeex.tech.web.auth", "com.seeex.tech.web.wecom", "com.seeex.tech.web.audit"}) public class MybatisPlusConfig {
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor());
        return interceptor;
    }
}
