package com.collabmodeler.api.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    public record LoginRequest(@Email @NotBlank String email, @NotBlank String password) {}
    public record RegisterRequest(@NotBlank String fullName, @Email @NotBlank String email, @NotBlank String password,
                                  @NotBlank String passwordConfirmation, @NotBlank String invitationToken) {}
    public record TokenRequest(@NotBlank String token) {}
    public record EmailRequest(@Email @NotBlank String email) {}
    public record ResetRequest(@NotBlank String token, @NotBlank String password, @NotBlank String passwordConfirmation) {}
    public record MeResponse(boolean authenticated, String email, String fullName, boolean verified,
                             boolean platformAdmin, String csrfToken) {}

    private final AuthService auth;
    private final AuthRateLimiter limits;
    private final ObjectProvider<ClientRegistrationRepository> oauthClients;
    public AuthController(AuthService auth, AuthRateLimiter limits, ObjectProvider<ClientRegistrationRepository> oauthClients) {
        this.auth = auth; this.limits = limits; this.oauthClients = oauthClients;
    }

    @GetMapping("/me")
    MeResponse me(Authentication authentication, CsrfToken csrf) {
        AccountPrincipal principal = principal(authentication);
        return principal == null
            ? new MeResponse(false, null, null, false, false, csrf.getToken())
            : new MeResponse(true, principal.email(), principal.fullName(), principal.verified(), principal.platformAdmin(), csrf.getToken());
    }

    @GetMapping("/invitations/{token}")
    AuthService.InvitationInfo invitation(@PathVariable String token) { return auth.invitation(token); }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    Map<String, String> register(@Valid @RequestBody RegisterRequest request, HttpServletRequest servletRequest) {
        limits.check("register", remote(servletRequest), 8, 900);
        auth.register(request.fullName(), request.email(), request.password(), request.passwordConfirmation(), request.invitationToken());
        return Map.of("message", "Revisa tu correo para verificar la cuenta.");
    }

    @PostMapping("/login")
    MeResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest servletRequest, CsrfToken csrf) {
        limits.check("login", remote(servletRequest) + ":" + AuthService.normalizeEmail(request.email()), 10, 900);
        AccountPrincipal principal = auth.login(request.email(), request.password());
        authenticate(principal, servletRequest);
        return new MeResponse(true, principal.email(), principal.fullName(), principal.verified(), principal.platformAdmin(), csrf.getToken());
    }

    @PostMapping("/verify")
    Map<String, Object> verify(@Valid @RequestBody TokenRequest request, HttpServletRequest servletRequest) {
        AuthService.Completion completion = auth.verify(request.token()); authenticate(completion.principal(), servletRequest);
        return completion.diagramId() == null ? Map.of("verified", true) : Map.of("verified", true, "diagramId", completion.diagramId());
    }

    @PostMapping("/verification/request")
    @ResponseStatus(HttpStatus.ACCEPTED)
    Map<String, String> resend(@Valid @RequestBody EmailRequest request, HttpServletRequest servletRequest) {
        limits.check("verify", remote(servletRequest), 5, 900); auth.resendVerification(request.email()); return genericMessage();
    }

    @PostMapping("/password/forgot")
    @ResponseStatus(HttpStatus.ACCEPTED)
    Map<String, String> forgot(@Valid @RequestBody EmailRequest request, HttpServletRequest servletRequest) {
        limits.check("forgot", remote(servletRequest), 5, 900); auth.requestReset(request.email()); return genericMessage();
    }

    @PostMapping("/password/reset")
    Map<String, String> reset(@Valid @RequestBody ResetRequest request, HttpServletRequest servletRequest) {
        limits.check("reset", remote(servletRequest), 8, 900);
        auth.resetPassword(request.token(), request.password(), request.passwordConfirmation());
        return Map.of("message", "Contraseña actualizada.");
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void logout(HttpServletRequest request, HttpServletResponse response) {
        HttpSession session = request.getSession(false); if (session != null) session.invalidate();
        SecurityContextHolder.clearContext(); response.setHeader("Clear-Site-Data", "\"cache\", \"cookies\", \"storage\"");
    }

    @GetMapping("/google")
    void google(@RequestParam(required = false) String invitationToken, HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (oauthClients.getIfAvailable() == null) { response.sendRedirect("/?authError=Google%20no%20est%C3%A1%20configurado%20en%20este%20entorno."); return; }
        if (invitationToken != null && !invitationToken.isBlank()) request.getSession(true).setAttribute("pendingInvitation", invitationToken);
        response.sendRedirect("/oauth2/authorization/google");
    }

    private static void authenticate(AccountPrincipal principal, HttpServletRequest request) {
        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of());
        SecurityContext context = SecurityContextHolder.createEmptyContext(); context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        request.getSession(true).setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
    }
    private static AccountPrincipal principal(Authentication authentication) {
        return authentication != null && authentication.isAuthenticated() && authentication.getPrincipal() instanceof AccountPrincipal value ? value : null;
    }
    private static String remote(HttpServletRequest request) { return request.getRemoteAddr() == null ? "unknown" : request.getRemoteAddr(); }
    private static Map<String, String> genericMessage() { return Map.of("message", "Si la cuenta existe, recibirás un correo con los siguientes pasos."); }
}
