package com.seeex.tech.file.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 文件元数据实体。
 *
 * <p>对应表 {@code file_metadata}。字段命名遵循小写 + 下划线与 Java 驼峰自动映射，
 * 状态、可见性、存储类别等可枚举字段以字符串常量收敛取值。</p>
 */
@TableName("file_metadata")
public class FileMetadataDO {

    private String applicationCode = "platform";
    /** 读取所属应用。 */
    public String getApplicationCode() { return applicationCode; }
    /** 设置所属应用。 */
    public void setApplicationCode(String value) { applicationCode = value; }

    /** 主键，自增。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 幂等键，唯一，防止重复创建。 */
    private String idempotencyKey;

    /** 租户 ID。 */
    private String tenantId;

    /** 所有者 ID。 */
    private String ownerId;

    /** 业务类型。 */
    private String bizType;

    /** 业务 ID。 */
    private String bizId;

    /** 存储桶名称。 */
    private String bucketName;

    /** 对象键，唯一。 */
    private String objectKey;

    /** 原始文件名。 */
    private String fileName;

    /** 内容类型。 */
    private String contentType;

    /** 文件大小（字节）。 */
    private Long fileSize;

    /** 文件 ETag。 */
    private String etag;

    /** 文件 SHA-256 摘要。 */
    private String sha256;

    /** 可见性。 */
    private String visibility;

    /** 状态：PENDING / CONFIRMED / DELETED。 */
    private String status;

    /** 存储类别。 */
    private String storageClass;

    /** 创建时间。 */
    private LocalDateTime createdAt;

    /** 确认时间。 */
    private LocalDateTime confirmedAt;

    /** 删除时间。 */
    private LocalDateTime deletedAt;

    /** 更新时间。 */
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(String ownerId) {
        this.ownerId = ownerId;
    }

    public String getBizType() {
        return bizType;
    }

    public void setBizType(String bizType) {
        this.bizType = bizType;
    }

    public String getBizId() {
        return bizId;
    }

    public void setBizId(String bizId) {
        this.bizId = bizId;
    }

    public String getBucketName() {
        return bucketName;
    }

    public void setBucketName(String bucketName) {
        this.bucketName = bucketName;
    }

    public String getObjectKey() {
        return objectKey;
    }

    public void setObjectKey(String objectKey) {
        this.objectKey = objectKey;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public Long getFileSize() {
        return fileSize;
    }

    public void setFileSize(Long fileSize) {
        this.fileSize = fileSize;
    }

    public String getEtag() {
        return etag;
    }

    public void setEtag(String etag) {
        this.etag = etag;
    }

    public String getSha256() {
        return sha256;
    }

    public void setSha256(String sha256) {
        this.sha256 = sha256;
    }

    public String getVisibility() {
        return visibility;
    }

    public void setVisibility(String visibility) {
        this.visibility = visibility;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getStorageClass() {
        return storageClass;
    }

    public void setStorageClass(String storageClass) {
        this.storageClass = storageClass;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getConfirmedAt() {
        return confirmedAt;
    }

    public void setConfirmedAt(LocalDateTime confirmedAt) {
        this.confirmedAt = confirmedAt;
    }

    public LocalDateTime getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(LocalDateTime deletedAt) {
        this.deletedAt = deletedAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
