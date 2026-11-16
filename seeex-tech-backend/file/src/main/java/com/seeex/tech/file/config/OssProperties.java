package com.seeex.tech.file.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 阿里云 OSS 配置项，绑定 {@code aliyun.oss} 前缀。
 *
 * @param endpoint         OSS 服务地址
 * @param bucket           存储桶名称
 * @param region           地域
 * @param accessKeyId      访问密钥 ID
 * @param accessKeySecret  访问密钥 Secret
 * @param privateBucket    是否为私有桶
 */
@ConfigurationProperties(prefix = "aliyun.oss")
public record OssProperties(String endpoint, String bucket, String region, String accessKeyId, String accessKeySecret,
                            boolean privateBucket) {
}
