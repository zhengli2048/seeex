package com.seeex.tech.web.exception;

import com.seeex.tech.common.api.ApiResponse;
import com.seeex.tech.common.api.ErrorCode;
import com.seeex.tech.common.api.ErrorType;
import com.seeex.tech.common.exception.BizException;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理。
 *
 * <p>统一将各类异常转换为 {@link ApiResponse} 失败响应，并按错误类型设置正确的 HTTP 状态码，
 * 保证对外不泄漏堆栈、SQL 等敏感信息，同时记录完整堆栈日志供排查。</p>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** 处理业务异常。 */
    @ExceptionHandler(BizException.class)
    public ResponseEntity<ApiResponse<Void>> handleBizException(BizException ex) {
        LOGGER.warn("业务异常: code={}, message={}", ex.code(), ex.getMessage());
        ErrorType type = ex.errorCode().errorType();
        return ResponseEntity.status(httpStatus(type)).body(ApiResponse.fail(ex.code(), type, ex.getMessage()));
    }

    /** 处理 {@code @Valid} 请求体校验失败。 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(fieldError -> fieldError.getField() + " " + fieldError.getDefaultMessage())
                .orElse(ErrorCode.VALIDATION_FAILED.defaultMessage());
        LOGGER.warn("参数校验失败: {}", message);
        return ResponseEntity.badRequest().body(ApiResponse.fail(ErrorCode.VALIDATION_FAILED.code(),
                ErrorCode.VALIDATION_FAILED.errorType(), message));
    }

    /** 处理方法参数上的约束校验失败。 */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(ConstraintViolationException ex) {
        LOGGER.warn("约束校验失败: {}", ex.getMessage());
        return ResponseEntity.badRequest().body(ApiResponse.fail(ErrorCode.VALIDATION_FAILED.code(),
                ErrorCode.VALIDATION_FAILED.errorType(), ErrorCode.VALIDATION_FAILED.defaultMessage()));
    }

    /** 处理缺失必需请求参数。 */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingParameter(MissingServletRequestParameterException ex) {
        String message = "缺少参数: " + ex.getParameterName();
        LOGGER.warn("{}", message);
        return ResponseEntity.badRequest().body(ApiResponse.fail(ErrorCode.VALIDATION_FAILED.code(),
                ErrorCode.VALIDATION_FAILED.errorType(), message));
    }

    /** 路径/查询参数类型和JSON结构错误统一按参数错误响应，避免误报服务器故障。 */
    @ExceptionHandler({org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,
            org.springframework.http.converter.HttpMessageNotReadableException.class,
            org.springframework.validation.BindException.class})
    public ResponseEntity<ApiResponse<Void>> handleMalformedRequest(Exception ex) {
        return ResponseEntity.badRequest().body(ApiResponse.fail(ErrorCode.VALIDATION_FAILED.code(),ErrorType.VALIDATION,"请求参数或数据格式不正确"));
    }

    /** 并发写入唯一键冲突使用稳定409，不向客户端返回SQL。 */
    @ExceptionHandler(org.springframework.dao.DuplicateKeyException.class)
    public ResponseEntity<ApiResponse<Void>> handleDuplicateKey(org.springframework.dao.DuplicateKeyException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.fail(ErrorCode.CONFLICT.code(),ErrorType.CONFLICT,"记录已存在，请刷新后重试"));
    }

    /** 处理其余未预期异常，统一收敛为系统内部错误。 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception ex) {
        LOGGER.error("系统内部错误", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.fail(ErrorCode.INTERNAL_ERROR.code(),
                ErrorCode.INTERNAL_ERROR.errorType(), ErrorCode.INTERNAL_ERROR.defaultMessage()));
    }

    /** 按错误类型映射 HTTP 状态码。 */
    private HttpStatus httpStatus(ErrorType type) {
        return switch (type) {
            case VALIDATION -> HttpStatus.BAD_REQUEST;
            case AUTHENTICATION -> HttpStatus.UNAUTHORIZED;
            case AUTHORIZATION -> HttpStatus.FORBIDDEN;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CONFLICT -> HttpStatus.CONFLICT;
            case RATE_LIMITED -> HttpStatus.TOO_MANY_REQUESTS;
            case BUSINESS -> HttpStatus.UNPROCESSABLE_ENTITY;
            case INTERNAL -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }
}
