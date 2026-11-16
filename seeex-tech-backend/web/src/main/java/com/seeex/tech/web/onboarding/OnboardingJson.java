package com.seeex.tech.web.onboarding;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seeex.tech.common.api.ErrorCode;
import com.seeex.tech.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
/** JSON与摘要只处理白名单DTO，不提供任意表达式求值。 */
@Component @RequiredArgsConstructor
public class OnboardingJson {
    private final ObjectMapper mapper;
    /** 编码受控业务对象。 */
    public String write(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch(com.fasterxml.jackson.core.JsonProcessingException ex) { throw invalid("业务字段无法序列化"); }
    }
    /** 解码已保存对象，损坏时明确拒绝。 */
    public <T> T read(String value,Class<T> type) {
        if(value==null) return null;
        try { return mapper.readValue(value,type); }
        catch(com.fasterxml.jackson.core.JsonProcessingException ex) { throw invalid("已保存业务字段格式无效"); }
    }
    /** SHA-256幂等内容摘要，不记录正文。 */
    public String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch(java.security.NoSuchAlgorithmException ex) { throw new BizException(ErrorCode.INTERNAL_ERROR,"摘要服务不可用"); }
    }
    /** 安全字段错误。 */
    public static BizException invalid(String message) { return new BizException(ErrorCode.VALIDATION_FAILED,message); }
    /** 版本或状态冲突，不自动重试旧请求。 */
    public static BizException conflict() { return new BizException(ErrorCode.CONFLICT,"记录已变化或任务已失效，请保留草稿并重新载入确认"); }
}
