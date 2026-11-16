package com.seeex.tech.common.exception;

import com.seeex.tech.common.api.ErrorCode;

/**
 * 业务异常。
 *
 * <p>业务处理失败时抛出本异常，由各服务的 {@code GlobalExceptionHandler} 统一转换为
 * {@link com.seeex.tech.common.api.ApiResponse} 失败响应。禁止在业务代码中直接捕获后吞掉，
 * 也禁止抛出裸 {@link IllegalArgumentException} 等通用异常替代业务异常。</p>
 */
public class BizException extends RuntimeException {

    /** 对应的稳定错误码。 */
    private final ErrorCode errorCode;

    /** 使用错误码默认消息构造。 */
    public BizException(ErrorCode errorCode) {
        super(errorCode.defaultMessage());
        this.errorCode = errorCode;
    }

    /** 使用自定义安全消息构造。 */
    public BizException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    /** 返回错误码。 */
    public ErrorCode errorCode() {
        return errorCode;
    }

    /** 返回错误码字符串。 */
    public String code() {
        return errorCode.code();
    }
}
