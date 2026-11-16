package com.seeex.tech.file.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class ServiceAuthFilter extends OncePerRequestFilter {
    @Value("${file.service-token:}")
    String token;

    /** 校验独立服务凭证，失败时返回统一认证错误。 */
    @Override
    protected void doFilterInternal(HttpServletRequest r, HttpServletResponse s, FilterChain c) throws ServletException, IOException {
        if(r.getRequestURI().startsWith("/actuator/health")) { c.doFilter(r,s); return; }
        String supplied=r.getHeader("X-Service-Token");
        if(token!=null && token.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>=32 && !"dev-only-token".equals(token) && supplied!=null && java.security.MessageDigest.isEqual(token.getBytes(java.nio.charset.StandardCharsets.UTF_8),supplied.getBytes(java.nio.charset.StandardCharsets.UTF_8))) { c.doFilter(r,s); return; }
        s.setStatus(401); s.setContentType("application/json;charset=UTF-8"); s.getWriter().write("{\"code\":\"UNAUTHORIZED\",\"error_type\":\"AUTHENTICATION\",\"message\":\"文件服务身份验证失败\"}");
    }
}
