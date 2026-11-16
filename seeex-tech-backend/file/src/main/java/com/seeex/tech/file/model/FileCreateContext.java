package com.seeex.tech.file.model;

/**
 * 文件创建上下文。
 *
 * <p>将创建文件元数据所需的十余项参数收敛为不可变对象，避免 {@code createPending}
 * 等方法的参数过长（参数个数不超过 5 个）。</p>
 *
 * @param idempotencyKey 幂等键，用于防止重复创建
 * @param objectKey      对象键（OSS 中的唯一标识）
 * @param fileName       原始文件名
 * @param contentType    内容类型
 * @param fileSize       文件大小（字节）
 * @param ownerId        所有者 ID
 * @param tenantId       租户 ID
 * @param bizType        业务类型（可为 null）
 * @param bizId          业务 ID（可为 null）
 * @param visibility     可见性
 */
public record FileCreateContext(
        String idempotencyKey,
        String objectKey,
        String fileName,
        String contentType,
        long fileSize,
        String ownerId,
        String tenantId,
        String bizType,
        String bizId,
        String visibility) {
}
