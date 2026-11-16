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
    @Value("${file.service-token}")
    String token;

    protected void doFilterInternal(HttpServletRequest r, HttpServletResponse s, FilterChain c) throws ServletException, IOException {
        if (r.getRequestURI().startsWith("/actuator/health") || token.equals(r.getHeader("X-Service-Token"))) {
            c.doFilter(r, s);
            return;
        }
        s.sendError(401, "unauthorized");
    }
}
