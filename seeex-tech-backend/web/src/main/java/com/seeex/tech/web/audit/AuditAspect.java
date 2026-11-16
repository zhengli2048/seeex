package com.seeex.tech.web.audit;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** 审计切面：拦截 {@link AuditLog} 标注的方法并记录审计。 */
@Aspect
@Component
@RequiredArgsConstructor
public class AuditAspect {

    private final AuditService auditService;
    private final AuditSnapshotService snapshots;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    @Around("@annotation(auditLog)")
    public Object around(ProceedingJoinPoint joinPoint, AuditLog auditLog) throws Throwable {
        String operator = currentOperator();
        String sourceIp = currentIp();
        String summary = "AUTH".equals(auditLog.resourceType()) ? "认证凭证已省略" : summarize(joinPoint.getArgs());
        Object before = null;
        try {
            if (!"AUTH".equals(auditLog.resourceType()) && RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) { before = snapshots.capture(attributes.getRequest()); }
            Object result = joinPoint.proceed();
            Object after = result instanceof com.seeex.tech.common.api.ApiResponse<?> response ? response.getData() : result;
            auditService.record(operator, auditLog.action(), auditLog.resourceType(), null, sourceIp,
                    "SUCCESS", summary, before == null ? null : safeJson(before), "EXPORT_AUDIT".equals(auditLog.action()) || "READ_WECOM_RAW".equals(auditLog.action()) || "AUTH".equals(auditLog.resourceType()) ? null : safeJson(after));
            return result;
        } catch (Throwable ex) {
            auditService.record(operator, auditLog.action(), auditLog.resourceType(), null, sourceIp,
                    "FAILED", summary, before == null ? null : safeJson(before), null);
            throw ex;
        }
    }

    private String currentOperator() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication == null ? null : authentication.getName();
    }

    private String currentIp() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            HttpServletRequest request = attributes.getRequest();
            return request.getRemoteAddr();
        }
        return null;
    }

    private String summarize(Object[] args) {
        String result = java.util.Arrays.stream(args).filter(arg -> !(arg instanceof Authentication) && !(arg instanceof HttpServletRequest))
                .map(this::safeJson).collect(java.util.stream.Collectors.joining(", "));
        return result.length() > 1000 ? result.substring(0, 1000) : result;
    }

    private String safeJson(Object value) {
        try {
            com.fasterxml.jackson.databind.JsonNode node = objectMapper.valueToTree(value);
            redact(node);
            String summary = objectMapper.writeValueAsString(node);
            return summary.length() > 4000 ? "\"内容过长，已省略\"" : summary;
        } catch (RuntimeException | com.fasterxml.jackson.core.JsonProcessingException ex) { return "\"无法序列化的业务对象\""; }
    }

    private void redact(com.fasterxml.jackson.databind.JsonNode node) {
        if (node == null) { return; }
        if (node.isObject()) {
            var names = new java.util.ArrayList<String>(); node.fieldNames().forEachRemaining(names::add);
            for (String name : names) {
                if (name.toLowerCase(java.util.Locale.ROOT).matches(".*(password|token|secret|aeskey|mobile|phone|email|payload).*")) {
                    ((com.fasterxml.jackson.databind.node.ObjectNode) node).put(name, "***");
                } else { redact(node.get(name)); }
            }
        } else if (node.isArray()) { node.forEach(this::redact); }
    }
}
