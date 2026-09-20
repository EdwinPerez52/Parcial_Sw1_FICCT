package com.collabmodeler.api.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

import java.util.Map;

@Configuration
public class SecurityConfig {
    @Bean
    SecurityFilterChain security(HttpSecurity http, ObjectMapper mapper) throws Exception {
        CookieCsrfTokenRepository csrf = CookieCsrfTokenRepository.withHttpOnlyFalse();
        http
            .csrf(value -> value.csrfTokenRepository(csrf))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health", "/error", "/api/v1/auth/**").permitAll()
                .anyRequest().authenticated())
            .exceptionHandling(errors -> errors
                .authenticationEntryPoint((request, response, exception) -> jsonError(response, mapper, 401, "UNAUTHORIZED", "Debes iniciar sesión."))
                .accessDeniedHandler((request, response, exception) -> jsonError(response, mapper, 403, "FORBIDDEN", "No tienes permiso para realizar esta acción.")))
            .sessionManagement(session -> session.sessionFixation(fixation -> fixation.changeSessionId()));
        return http.build();
    }

    @Bean PasswordEncoder passwordEncoder() { return Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8(); }

    private static void jsonError(HttpServletResponse response, ObjectMapper mapper, int status, String code, String detail) throws java.io.IOException {
        response.setStatus(status); response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        mapper.writeValue(response.getOutputStream(), Map.of("status", status, "code", code, "detail", detail));
    }
}

