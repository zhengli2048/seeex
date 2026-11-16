package com.seeex.tech.file.service;

import com.aliyun.oss.HttpMethod;
import com.aliyun.oss.OSS;
import com.aliyun.oss.model.GeneratePresignedUrlRequest;
import com.aliyun.oss.model.ResponseHeaderOverrides;
import com.seeex.tech.file.config.OssProperties;
import org.springframework.stereotype.Service;

import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * 阿里云 OSS 存储适配。
 *
 * <p>封装对象上传、下载预签名 URL 的生成以及对象删除，屏蔽 OSS SDK 细节。</p>
 */
@Service
public class OssStorageService {

    private final OSS client;
    private final OssProperties properties;

    public OssStorageService(OSS client, OssProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    /**
     * 生成 PUT 预签名上传 URL。
     *
     * @param objectKey    对象键
     * @param expireMillis 有效期（毫秒）
     * @return 预签名上传 URL
     */
    public URL createUploadUrl(String objectKey, long expireMillis) {
        GeneratePresignedUrlRequest request = buildRequest(objectKey, HttpMethod.PUT, expireMillis);
        return client.generatePresignedUrl(request);
    }

    /**
     * 生成 GET 预签名下载 URL。
     *
     * @param objectKey    对象键
     * @param expireMillis 有效期（毫秒）
     * @return 预签名下载 URL
     */
    public URL createDownloadUrl(String objectKey, long expireMillis) {
        GeneratePresignedUrlRequest request = buildRequest(objectKey, HttpMethod.GET, expireMillis);
        return client.generatePresignedUrl(request);
    }

    /**
     * 生成带附件下载文件名的 GET 预签名 URL。
     *
     * @param objectKey    对象键
     * @param fileName     下载时展示的文件名
     * @param expireMillis 有效期（毫秒）
     * @return 预签名下载 URL
     */
    public URL createDownloadUrl(String objectKey, String fileName, long expireMillis) {
        GeneratePresignedUrlRequest request = buildRequest(objectKey, HttpMethod.GET, expireMillis);
        ResponseHeaderOverrides headers = new ResponseHeaderOverrides();
        headers.setContentDisposition("attachment; filename*=UTF-8''"
                + URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20"));
        request.setResponseHeaders(headers);
        return client.generatePresignedUrl(request);
    }

    /**
     * 判断对象是否存在。
     *
     * @param objectKey 对象键
     * @return 存在返回 true
     */
    public boolean exists(String objectKey) {
        return client.doesObjectExist(properties.bucket(), objectKey);
    }

    /** 上传确认时校验真实对象大小与类型。 */
    public boolean matches(String objectKey, long expectedSize, String expectedType) {
        if (!exists(objectKey)) { return false; }
        var metadata = client.getObjectMetadata(properties.bucket(), objectKey);
        String actualType = metadata.getContentType();
        return metadata.getContentLength() == expectedSize && actualType != null && expectedType.equalsIgnoreCase(actualType.split(";")[0].trim());
    }

    /**
     * 删除对象（不存在时静默跳过）。
     *
     * @param objectKey 对象键
     */
    public void delete(String objectKey) {
        if (exists(objectKey)) {
            client.deleteObject(properties.bucket(), objectKey);
        }
    }

    /**
     * 构造预签名请求。
     *
     * @param objectKey    对象键
     * @param method       HTTP 方法
     * @param expireMillis 有效期（毫秒）
     * @return 预签名请求
     */
    private GeneratePresignedUrlRequest buildRequest(String objectKey, HttpMethod method, long expireMillis) {
        Date expiration = new Date(System.currentTimeMillis() + expireMillis);
        GeneratePresignedUrlRequest request = new GeneratePresignedUrlRequest(properties.bucket(), objectKey, method);
        request.setExpiration(expiration);
        return request;
    }
}
