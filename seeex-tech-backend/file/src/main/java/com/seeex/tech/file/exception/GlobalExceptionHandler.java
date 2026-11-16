package com.seeex.tech.file.exception;

import com.seeex.tech.common.api.ApiResponse;
import com.seeex.tech.common.api.ErrorCode;
import com.seeex.tech.common.exception.BizException;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 文件服务全局异常处理。
 *
 * <p>与 {@code web} 模块保持一致的错误响应契约：统一将异常转换为
 * {@link ApiResponse} 失败响应，不泄漏堆栈与敏感信息。</p>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 处理业务异常。
     */
    @ExceptionHandler(BizException.class)
    public ApiResponse<Void> handleBizException(BizException ex) {
        LOGGER.warn("业务异常: code={}, message={}", ex.code(), ex.getMessage());
        return ApiResponse.fail(ex.code(), ex.errorCode().errorType(), ex.getMessage());
    }

    /**
     * 处理 {@code @Valid} 请求体校验失败。
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ApiResponse<Void> handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(fieldError -> fieldError.getField() + " " + fieldError.getDefaultMessage())
                .orElse(ErrorCode.VALIDATION_FAILED.defaultMessage());
        LOGGER.warn("参数校验失败: {}", message);
        return ApiResponse.fail(ErrorCode.VALIDATION_FAILED.code(),
                ErrorCode.VALIDATION_FAILED.errorType(), message);
    }

    /**
     * 处理方法参数上的约束校验失败。
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ApiResponse<Void> handleConstraintViolation(ConstraintViolationException ex) {
        LOGGER.warn("约束校验失败: {}", ex.getMessage());
        return ApiResponse.fail(ErrorCode.VALIDATION_FAILED.code(),
                ErrorCode.VALIDATION_FAILED.errorType(), ErrorCode.VALIDATION_FAILED.defaultMessage());
    }

    /**
     * 处理缺失必需请求参数。
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ApiResponse<Void> handleMissingParameter(MissingServletRequestParameterException ex) {
        String message = "缺少参数: " + ex.getParameterName();
        LOGGER.warn("{}", message);
        return ApiResponse.fail(ErrorCode.VALIDATION_FAILED.code(),
                ErrorCode.VALIDATION_FAILED.errorType(), message);
    }

    /**
     * 处理其余未预期异常，统一收敛为系统内部错误。
     */
    @ExceptionHandler(Exception.class)
    public ApiResponse<Void> handleException(Exception ex) {
        LOGGER.error("系统内部错误", ex);
        return ApiResponse.fail(ErrorCode.INTERNAL_ERROR.code(),
                ErrorCode.INTERNAL_ERROR.errorType(), ErrorCode.INTERNAL_ERROR.defaultMessage());
    }
}
