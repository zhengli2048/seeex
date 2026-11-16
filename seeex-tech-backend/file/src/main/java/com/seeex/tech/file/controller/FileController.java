package com.seeex.tech.file.controller;

import com.seeex.tech.common.api.ApiResponse;
import com.seeex.tech.common.api.ErrorCode;
import com.seeex.tech.common.exception.BizException;
import com.seeex.tech.file.model.FileCreateContext;
import com.seeex.tech.file.service.FileMetadataService;
import com.seeex.tech.file.service.OssStorageService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URL;
import java.util.UUID;
import java.util.List;

/**
 * 文件接口。
 *
 * <p>提供文件上传 URL 申请、上传确认、下载 URL 申请与删除能力，
 * 面向服务间调用，通过请求头传递调用方身份与幂等键。</p>
 */
@RestController
@RequestMapping("/api/files")
public class FileController {

    /** 单文件大小上限：50MB。 */
    private static final long MAX_FILE_SIZE_BYTES = 50L * 1024 * 1024;

    /** 允许的内容类型正则。 */
    private static final String ALLOWED_CONTENT_TYPE_PATTERN = "image/(png|jpeg|webp)|application/pdf|text/plain";

    /** 上传 URL 有效期（秒）。 */
    private static final long UPLOAD_URL_EXPIRE_SECONDS = 600;

    /** 下载 URL 有效期（秒）。 */
    private static final long DOWNLOAD_URL_EXPIRE_SECONDS = 300;

    /** 对象键前缀。 */
    private static final String OBJECT_KEY_PREFIX = "uploads/";

    /** 文件名非法字符替换规则。 */
    private static final String FILE_NAME_SANITIZE_PATTERN = "[^a-zA-Z0-9._-]";

    /** 默认可见性。 */
    private static final String DEFAULT_VISIBILITY = "PRIVATE";

    private final OssStorageService storage;
    private final FileMetadataService metadata;

    public FileController(OssStorageService storage, FileMetadataService metadata) {
        this.storage = storage;
        this.metadata = metadata;
    }

    /**
     * 申请上传 URL。
     *
     * @param fileName    原始文件名
     * @param contentType 内容类型
     * @param size        文件大小（字节）
     * @param idem        幂等键
     * @param owner       所有者 ID
     * @param tenant      租户 ID
     * @param bizType     业务类型（可选）
     * @param bizId       业务 ID（可选）
     * @param visibility  可见性
     * @return 上传 URL 响应
     */
    @PostMapping("/upload-url")
    public ApiResponse<UploadUrlResponse> createUploadUrl(
            @RequestParam String fileName,
            @RequestParam String contentType,
            @RequestParam long size,
            @RequestHeader("Idempotency-Key") String idem,
            @RequestHeader("X-User-Id") String owner,
            @RequestHeader("X-Tenant-Id") String tenant,
            @RequestParam(required = false) String bizType,
            @RequestParam(required = false) String bizId,
            @RequestParam(defaultValue = DEFAULT_VISIBILITY) String visibility) {
        validateUploadRequest(contentType, size);
        String objectKey = buildObjectKey(fileName);
        FileCreateContext context = new FileCreateContext(
                idem, objectKey, fileName, contentType, size, owner, tenant, bizType, bizId, visibility);
        String persistedObjectKey = metadata.createPending(context);
        URL url = storage.createUploadUrl(persistedObjectKey, UPLOAD_URL_EXPIRE_SECONDS * 1000L);
        return ApiResponse.ok(new UploadUrlResponse(persistedObjectKey, url.toString(), UPLOAD_URL_EXPIRE_SECONDS));
    }

    /**
     * 确认上传完成。
     *
     * @param objectKey 对象键
     * @param owner     所有者 ID
     * @param tenant    租户 ID
     * @return 空响应
     */
    @PostMapping("/confirm")
    public ApiResponse<Void> confirm(
            @RequestParam String objectKey,
            @RequestHeader("X-User-Id") String owner,
            @RequestHeader("X-Tenant-Id") String tenant) {
        var pending = metadata.pending(objectKey, owner, tenant);
        if (!storage.matches(objectKey, pending.getFileSize(), pending.getContentType())) { throw new BizException(ErrorCode.CONFLICT, "文件尚未上传完成或大小、类型不匹配"); }
        metadata.confirm(objectKey, owner, tenant);
        return ApiResponse.ok(null);
    }

    /** 查询当前租户文件列表。 */
    @GetMapping
    public ApiResponse<FileMetadataService.PageResult> list(
            @RequestHeader("X-User-Id") String owner,
            @RequestHeader("X-Tenant-Id") String tenant,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(name = "page_size", defaultValue = "20") int pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String contentType) {
        return ApiResponse.ok(metadata.list(owner, tenant, Math.max(page, 1), Math.min(Math.max(pageSize, 1), 100), keyword, status, contentType));
    }

    /**
     * 申请下载 URL。
     *
     * @param objectKey 对象键
     * @param owner     所有者 ID
     * @param tenant    租户 ID
     * @return 下载 URL
     */
    @GetMapping("/download-url")
    public ApiResponse<String> download(
            @RequestParam String objectKey,
            @RequestHeader("X-User-Id") String owner,
            @RequestHeader("X-Tenant-Id") String tenant) {
        String fileName = metadata.fileName(objectKey, owner, tenant);
        String url = storage.createDownloadUrl(objectKey, fileName, DOWNLOAD_URL_EXPIRE_SECONDS * 1000L).toString();
        return ApiResponse.ok(url);
    }

    /**
     * 删除文件。
     *
     * @param objectKey 对象键
     * @param owner     所有者 ID
     * @param tenant    租户 ID
     * @return 空响应
     */
    @DeleteMapping
    public ApiResponse<Void> delete(
            @RequestParam String objectKey,
            @RequestHeader("X-User-Id") String owner,
            @RequestHeader("X-Tenant-Id") String tenant) {
        metadata.fileName(objectKey, owner, tenant);
        storage.delete(objectKey);
        metadata.markDeleted(objectKey, owner, tenant);
        return ApiResponse.ok(null);
    }

    /** 批量删除当前所有者的文件。 */
    @DeleteMapping("/batch")
    public ApiResponse<Void> batchDelete(
            @org.springframework.web.bind.annotation.RequestBody List<String> objectKeys,
            @RequestHeader("X-User-Id") String owner,
            @RequestHeader("X-Tenant-Id") String tenant) {
        if (objectKeys == null || objectKeys.isEmpty() || objectKeys.size() > 100) { throw new BizException(ErrorCode.VALIDATION_FAILED, "请选择文件"); }
        // 整批校验通过后才产生存储副作用，每个成功项立即同步元数据。
        objectKeys.forEach(key -> metadata.fileName(key, owner, tenant));
        objectKeys.forEach(key -> { storage.delete(key); metadata.markDeleted(key, owner, tenant); });
        return ApiResponse.ok(null);
    }

    /**
     * 校验上传参数。
     *
     * @param contentType 内容类型
     * @param size        文件大小（字节）
     */
    private void validateUploadRequest(String contentType, long size) {
        if (size <= 0 || size > MAX_FILE_SIZE_BYTES) {
            throw new BizException(ErrorCode.VALIDATION_FAILED, "文件大小须在 1 字节到 50MB 之间");
        }
        if (!contentType.matches(ALLOWED_CONTENT_TYPE_PATTERN)) {
            throw new BizException(ErrorCode.VALIDATION_FAILED, "文件类型不允许");
        }
    }

    /**
     * 生成安全对象键。
     *
     * @param fileName 原始文件名
     * @return 对象键
     */
    private String buildObjectKey(String fileName) {
        String sanitized = fileName.replaceAll(FILE_NAME_SANITIZE_PATTERN, "_");
        return OBJECT_KEY_PREFIX + UUID.randomUUID() + "/" + sanitized;
    }

    /**
     * 上传 URL 响应。
     *
     * @param objectKey        对象键
     * @param uploadUrl        上传 URL
     * @param expiresInSeconds 有效期（秒）
     */
    public record UploadUrlResponse(String objectKey, String uploadUrl, long expiresInSeconds) {
    }
}
