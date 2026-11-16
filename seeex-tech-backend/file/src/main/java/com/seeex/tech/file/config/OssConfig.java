package com.seeex.tech.file.config;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OSS 客户端配置。
 */
@Configuration
@EnableConfigurationProperties(OssProperties.class)
public class OssConfig {

    /**
     * 构建 OSS 客户端 Bean。
     *
     * @param properties OSS 配置项
     * @return OSS 客户端
     */
    @Bean(destroyMethod = "shutdown")
    OSS ossClient(OssProperties properties) {
        return new OSSClientBuilder().build(properties.endpoint(), properties.accessKeyId(), properties.accessKeySecret());
    }
}
