package com.seeex.tech.common.api;

/**
 * 机器可读的错误分类，用于 {@link ApiResponse} 的 {@code error_type} 字段。
 *
 * <p>与 HTTP 状态码保持语义对应：{@link #VALIDATION} / {@link #AUTHENTICATION} /
 * {@link #AUTHORIZATION} / {@link #NOT_FOUND} / {@link #CONFLICT} / {@link #RATE_LIMITED}
 * 对应 4xx，{@link #INTERNAL} 对应 5xx，{@link #BUSINESS} 视场景映射。</p>
 */
public enum ErrorType {

    /** 参数校验失败。 */
    VALIDATION,
    /** 未认证（未登录或凭证无效）。 */
    AUTHENTICATION,
    /** 已认证但无权限。 */
    AUTHORIZATION,
    /** 资源不存在。 */
    NOT_FOUND,
    /** 请求与当前资源状态冲突。 */
    CONFLICT,
    /** 触发限流。 */
    RATE_LIMITED,
    /** 业务处理失败。 */
    BUSINESS,
    /** 系统内部错误。 */
    INTERNAL;

    /**
     * 根据错误码前缀推断错误分类。
     *
     * @param code 错误码，可能为 null
     * @return 匹配的分类；null 返回 {@link #INTERNAL}，无前缀匹配时返回 {@link #BUSINESS}
     */
    public static ErrorType fromCode(String code) {
        if (code == null) {
            return INTERNAL;
        }
        String normalized = code.toUpperCase();
        for (ErrorType type : values()) {
            if (normalized.startsWith(type.name() + "_")) {
                return type;
            }
        }
        return BUSINESS;
    }
}
