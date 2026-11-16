package com.seeex.tech.web.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {

    /** 密码编码器。 */
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, BearerAuthFilter bearerAuthFilter) throws Exception {
        http.csrf(csrf -> csrf.disable()).sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(bearerAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .exceptionHandling(errors -> errors.authenticationEntryPoint((request, response, failure) -> {
                    response.setStatus(401); response.setContentType("application/json;charset=UTF-8");
                    response.getWriter().write("{\"code\":\"UNAUTHORIZED\",\"error_type\":\"AUTHENTICATION\",\"message\":\"请先登录\"}");
                }).accessDeniedHandler((request, response, failure) -> {
                    response.setStatus(403); response.setContentType("application/json;charset=UTF-8");
                    response.getWriter().write("{\"code\":\"FORBIDDEN\",\"error_type\":\"AUTHORIZATION\",\"message\":\"暂无访问权限\"}");
                }))
                .authorizeHttpRequests(auth -> auth.requestMatchers(org.springframework.http.HttpMethod.POST, "/api/platform/apps/*/route-metadata", "/api/sso/token/exchange", "/api/app-auth/permissions", "/api/app-auth/check", "/api/app-auth/data-scope", "/api/app-auth/check-data").permitAll().requestMatchers("/actuator/health/**", "/api/auth/**", "/api/integrations/wecom/events").permitAll().anyRequest().authenticated());
        return http.build();
    }
}
