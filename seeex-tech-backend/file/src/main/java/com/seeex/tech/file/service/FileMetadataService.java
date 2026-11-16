package com.seeex.tech.file.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.seeex.tech.common.api.ErrorCode;
import com.seeex.tech.common.exception.BizException;
import com.seeex.tech.file.config.OssProperties;
import com.seeex.tech.file.entity.FileMetadataDO;
import com.seeex.tech.file.mapper.FileMetadataMapper;
import com.seeex.tech.file.model.FileCreateContext;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 文件元数据服务。
 *
 * <p>负责文件元数据的持久化与查询。数据访问统一使用 MyBatis-Plus
 * （{@code BaseMapper} 与 {@code Lambda} 条件构造器），禁止手写 SQL。</p>
 */
@Service
public class FileMetadataService {

    /** 状态：待确认。 */
    private static final String STATUS_PENDING = "PENDING";

    /** 状态：已确认。 */
    private static final String STATUS_CONFIRMED = "CONFIRMED";

    /** 状态：已删除。 */
    private static final String STATUS_DELETED = "DELETED";


    private final FileMetadataMapper fileMetadataMapper;
    private final OssProperties ossProperties;

    public FileMetadataService(FileMetadataMapper fileMetadataMapper, OssProperties ossProperties) {
        this.fileMetadataMapper = fileMetadataMapper;
        this.ossProperties = ossProperties;
    }

    /**
     * 创建待确认状态的文件元数据（幂等）。
     *
     * <p>通过 {@code idempotency_key} 唯一约束保证重复请求不产生重复记录；重复时静默忽略。</p>
     *
     * @param context 文件创建上下文
     */
    public String createPending(FileCreateContext context) {
        FileMetadataDO existing = fileMetadataMapper.selectOne(Wrappers.<FileMetadataDO>lambdaQuery()
                .eq(FileMetadataDO::getIdempotencyKey, context.idempotencyKey()));
        if (existing != null) {
            if (!application().equals(existing.getApplicationCode())) { throw new BizException(ErrorCode.CONFLICT,"幂等键属于其他应用"); }
            if (!java.util.Objects.equals(existing.getOwnerId(), context.ownerId()) || !java.util.Objects.equals(existing.getTenantId(), context.tenantId())) { throw new BizException(ErrorCode.CONFLICT, "幂等键已被使用"); }
            if (!STATUS_PENDING.equals(existing.getStatus()) || !java.util.Objects.equals(existing.getFileSize(), context.fileSize()) || !java.util.Objects.equals(existing.getContentType(), context.contentType())) { throw new BizException(ErrorCode.CONFLICT, "幂等上传请求已完成或参数不同"); }
            return existing.getObjectKey();
        }
        FileMetadataDO entity = buildPendingEntity(context);
        try {
            fileMetadataMapper.insert(entity);
        } catch (DuplicateKeyException ex) {
            FileMetadataDO winner = fileMetadataMapper.selectOne(Wrappers.<FileMetadataDO>lambdaQuery().eq(FileMetadataDO::getIdempotencyKey, context.idempotencyKey()));
            if (winner == null || !application().equals(winner.getApplicationCode()) || !STATUS_PENDING.equals(winner.getStatus()) || !java.util.Objects.equals(winner.getFileSize(),context.fileSize()) || !java.util.Objects.equals(winner.getContentType(),context.contentType()) || !java.util.Objects.equals(winner.getOwnerId(), context.ownerId()) || !java.util.Objects.equals(winner.getTenantId(), context.tenantId())) { throw new BizException(ErrorCode.CONFLICT, "上传请求冲突"); }
            return winner.getObjectKey();
        }
        return entity.getObjectKey();
    }

    /** 上传确认前校验归属与待确认状态。 */
    public FileMetadataDO pending(String objectKey, String owner, String tenant) {
        FileMetadataDO row = fileMetadataMapper.selectOne(Wrappers.<FileMetadataDO>lambdaQuery().eq(FileMetadataDO::getObjectKey, objectKey)
                .eq(FileMetadataDO::getOwnerId, owner).eq(FileMetadataDO::getTenantId, tenant).eq(FileMetadataDO::getApplicationCode,application()).eq(FileMetadataDO::getStatus, STATUS_PENDING));
        if (row == null) { throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "待确认文件不存在或无权访问"); } return row;
    }

    /**
     * 确认文件已上传完成。
     *
     * @param objectKey 对象键
     * @param ownerId   所有者 ID
     * @param tenantId  租户 ID
     */
    public void confirm(String objectKey, String ownerId, String tenantId) {
        LambdaUpdateWrapper<FileMetadataDO> wrapper = buildUpdateWrapper(objectKey, ownerId, tenantId)
                .set(FileMetadataDO::getStatus, STATUS_CONFIRMED)
                .set(FileMetadataDO::getUpdatedAt, LocalDateTime.now());
        int affected = fileMetadataMapper.update(null, wrapper.eq(FileMetadataDO::getStatus, STATUS_PENDING));
        if (affected == 0) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "pending file not found or already confirmed");
        }
    }

    /**
     * 标记文件为已删除（软删除）。
     *
     * @param objectKey 对象键
     * @param ownerId   所有者 ID
     * @param tenantId  租户 ID
     */
    public void markDeleted(String objectKey, String ownerId, String tenantId) {
        LambdaUpdateWrapper<FileMetadataDO> wrapper = buildUpdateWrapper(objectKey, ownerId, tenantId)
                .set(FileMetadataDO::getStatus, STATUS_DELETED)
                .set(FileMetadataDO::getUpdatedAt, LocalDateTime.now());
        int affected = fileMetadataMapper.update(null, wrapper.eq(FileMetadataDO::getStatus, STATUS_CONFIRMED));
        if (affected == 0) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "confirmed file not found or already deleted");
        }
    }

    /** 分页查询当前租户和所有者可见的文件。 */
    public PageResult list(String ownerId, String tenantId, int page, int pageSize, String keyword, String status, String contentType) {
        LambdaQueryWrapper<FileMetadataDO> wrapper = Wrappers.<FileMetadataDO>lambdaQuery()
                .eq(FileMetadataDO::getOwnerId, ownerId)
                .eq(FileMetadataDO::getTenantId, tenantId).eq(FileMetadataDO::getApplicationCode,application())
                .ne(FileMetadataDO::getStatus, STATUS_DELETED)
                .eq(contentType != null && !contentType.isBlank(), FileMetadataDO::getContentType, contentType)
                .eq(status != null && !status.isBlank(), FileMetadataDO::getStatus, status)
                .and(keyword != null && !keyword.isBlank(), query -> query.like(FileMetadataDO::getFileName, keyword)
                        .or().like(FileMetadataDO::getBizType, keyword).or().like(FileMetadataDO::getBizId, keyword))
                .orderByDesc(FileMetadataDO::getId);
        Page<FileMetadataDO> result = fileMetadataMapper.selectPage(new Page<>(page, pageSize), wrapper);
        List<FileItem> items = result.getRecords().stream().map(FileMetadataService::toItem).toList();
        return new PageResult(items, result.getTotal());
    }

    /** 批量软删除文件。 */
    public void markDeleted(List<String> objectKeys, String ownerId, String tenantId) {
        if (objectKeys == null || objectKeys.isEmpty()) {
            throw new BizException(ErrorCode.VALIDATION_FAILED, "objectKeys must not be empty");
        }
        objectKeys.forEach(key -> markDeleted(key, ownerId, tenantId));
    }

    /**
     * 查询已确认文件的原始文件名。
     *
     * @param objectKey 对象键
     * @param ownerId   所有者 ID
     * @param tenantId  租户 ID
     * @return 原始文件名
     * @throws BizException 文件不存在或无权访问时抛出
     */
    public String fileName(String objectKey, String ownerId, String tenantId) {
        LambdaQueryWrapper<FileMetadataDO> wrapper = Wrappers.<FileMetadataDO>lambdaQuery()
                .select(FileMetadataDO::getFileName)
                .eq(FileMetadataDO::getObjectKey, objectKey)
                .eq(FileMetadataDO::getOwnerId, ownerId)
                .eq(FileMetadataDO::getTenantId, tenantId).eq(FileMetadataDO::getApplicationCode,application())
                .eq(FileMetadataDO::getStatus, STATUS_CONFIRMED);
        FileMetadataDO entity = fileMetadataMapper.selectOne(wrapper);
        if (entity == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "文件不存在或无权访问");
        }
        return entity.getFileName();
    }

    /**
     * 由创建上下文构造待确认实体。
     *
     * @param context 文件创建上下文
     * @return 待确认状态的文件元数据实体
     */
    private FileMetadataDO buildPendingEntity(FileCreateContext context) {
        FileMetadataDO entity = new FileMetadataDO();
        entity.setApplicationCode(application());
        entity.setIdempotencyKey(context.idempotencyKey());
        entity.setObjectKey(context.objectKey());
        entity.setFileName(context.fileName());
        entity.setContentType(context.contentType());
        entity.setFileSize(context.fileSize());
        entity.setOwnerId(context.ownerId());
        entity.setTenantId(context.tenantId());
        entity.setBizType(context.bizType());
        entity.setBizId(context.bizId());
        entity.setVisibility(context.visibility());
        entity.setBucketName(ossProperties.bucket());
        entity.setStatus(STATUS_PENDING);
        return entity;
    }

    /**
     * 构造按对象键 + 所有者 + 租户过滤的更新条件。
     *
     * @param objectKey 对象键
     * @param ownerId   所有者 ID
     * @param tenantId  租户 ID
     * @return 更新条件构造器
     */
    private LambdaUpdateWrapper<FileMetadataDO> buildUpdateWrapper(String objectKey, String ownerId, String tenantId) {
        return Wrappers.<FileMetadataDO>lambdaUpdate()
                .eq(FileMetadataDO::getObjectKey, objectKey)
                .eq(FileMetadataDO::getOwnerId, ownerId)
                .eq(FileMetadataDO::getTenantId, tenantId).eq(FileMetadataDO::getApplicationCode,application());
    }

    /** 服务令牌已由过滤器验证，应用边界由可信网关请求头提供。 */
    private String application() {
        String value = "platform";
        if (org.springframework.web.context.request.RequestContextHolder.getRequestAttributes() instanceof org.springframework.web.context.request.ServletRequestAttributes attrs) { value = attrs.getRequest().getHeader("X-App-Code"); if (value == null) value = "platform"; }
        if (!value.matches("[a-z][a-z0-9-]{1,127}")) { throw new BizException(ErrorCode.VALIDATION_FAILED,"应用编码无效"); }
        return value;
    }

    private static FileItem toItem(FileMetadataDO entity) {
        return new FileItem(entity.getId(), entity.getObjectKey(), entity.getFileName(), entity.getContentType(),
                entity.getFileSize(), entity.getBizType(), entity.getBizId(), entity.getVisibility(), entity.getStatus(),
                entity.getCreatedAt(), entity.getConfirmedAt(), entity.getOwnerId(), entity.getTenantId(), entity.getUpdatedAt(), entity.getApplicationCode());
    }

    /** 文件列表响应。 */
    public record PageResult(List<FileItem> items, long total) { }

    /** 文件元数据视图。 */
    public record FileItem(Long id, String objectKey, String fileName, String contentType, Long fileSize,
                           String bizType, String bizId, String visibility, String status,
                           LocalDateTime createdAt, LocalDateTime confirmedAt, String ownerId, String tenantId, LocalDateTime updatedAt, String applicationCode) { }
}
