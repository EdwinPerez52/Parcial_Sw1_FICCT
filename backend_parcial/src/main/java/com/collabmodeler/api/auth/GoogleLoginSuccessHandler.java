package com.collabmodeler.api.auth;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
public class GoogleLoginSuccessHandler implements AuthenticationSuccessHandler {
    private final AuthService auth;
    public GoogleLoginSuccessHandler(AuthService auth) { this.auth = auth; }

    @Override public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                                   org.springframework.security.core.Authentication authentication) throws IOException, ServletException {
        try {
            OidcUser google = (OidcUser) authentication.getPrincipal();
            String invitation = (String) request.getSession().getAttribute("pendingInvitation");
            AuthService.Completion result = auth.googleLogin(google.getEmail(), google.getSubject(), google.getFullName(),
                Boolean.TRUE.equals(google.getEmailVerified()), invitation);
            var local = UsernamePasswordAuthenticationToken.authenticated(result.principal(), null, List.of());
            SecurityContext context = SecurityContextHolder.createEmptyContext(); context.setAuthentication(local);
            SecurityContextHolder.setContext(context);
            request.getSession().removeAttribute("pendingInvitation");
            request.getSession().setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
            response.sendRedirect(result.diagramId() == null ? "/" : "/?diagram=" + result.diagramId());
        } catch (AuthException exception) {
            response.sendRedirect("/?authError=" + URLEncoder.encode(exception.getMessage(), StandardCharsets.UTF_8));
        }
    }
}
