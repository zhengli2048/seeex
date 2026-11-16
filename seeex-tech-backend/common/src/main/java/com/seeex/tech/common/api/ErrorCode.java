package com.seeex.tech.common.api;

/**
 * 稳定错误码枚举。
 *
 * <p>错误码使用大写下划线格式且保持稳定，禁止随意变更；新增错误码须同步更新
 * 根目录 {@code AGENTS.md} 与 {@code CLAUDE.md} 中的 API 契约和项目记忆章节。</p>
 */
public enum ErrorCode {

    /** 请求参数校验失败。 */
    VALIDATION_FAILED("VALIDATION_FAILED", ErrorType.VALIDATION, "请求参数校验失败"),
    /** 未登录或凭证无效。 */
    UNAUTHORIZED("UNAUTHORIZED", ErrorType.AUTHENTICATION, "请先登录"),
    /** 无访问权限。 */
    FORBIDDEN("FORBIDDEN", ErrorType.AUTHORIZATION, "没有访问权限"),
    /** 资源不存在。 */
    RESOURCE_NOT_FOUND("RESOURCE_NOT_FOUND", ErrorType.NOT_FOUND, "资源不存在"),
    /** 请求与当前资源状态冲突。 */
    CONFLICT("CONFLICT", ErrorType.CONFLICT, "请求与当前资源状态冲突"),
    /** 触发限流。 */
    RATE_LIMITED("RATE_LIMITED", ErrorType.RATE_LIMITED, "请求过于频繁"),
    /** 业务处理失败。 */
    BUSINESS_ERROR("BUSINESS_ERROR", ErrorType.BUSINESS, "业务处理失败"),
    /** 系统内部错误。 */
    INTERNAL_ERROR("INTERNAL_ERROR", ErrorType.INTERNAL, "系统内部错误");

    private final String code;
    private final ErrorType errorType;
    private final String defaultMessage;

    ErrorCode(String code, ErrorType errorType, String defaultMessage) {
        this.code = code;
        this.errorType = errorType;
        this.defaultMessage = defaultMessage;
    }

    public String code() {
        return code;
    }

    public ErrorType errorType() {
        return errorType;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
