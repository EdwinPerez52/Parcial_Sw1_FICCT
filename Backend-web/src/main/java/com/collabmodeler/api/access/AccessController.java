package com.collabmodeler.api.access;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import com.collabmodeler.api.auth.AccountPrincipal;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class AccessController {
    private final AccessService access;
    public AccessController(AccessService access) { this.access = access; }

    @PostMapping("/diagrams/{id}/share-link")
    Map<String, String> rotate(@PathVariable UUID id, Principal principal) { requireVerified(principal); return access.rotateLink(id, subject(principal)); }

    @DeleteMapping("/diagrams/{id}/share-link")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void revoke(@PathVariable UUID id, Principal principal) { requireVerified(principal); access.revokeLink(id, subject(principal)); }

    @PostMapping("/join/{token}")
    Map<String, UUID> join(@PathVariable String token, Principal principal) {
        requireVerified(principal);
        return Map.of("diagramId", access.join(token, subject(principal), displayName(principal)));
    }

    @GetMapping("/diagrams/{id}/members")
    List<DiagramMemberEntity> members(@PathVariable UUID id, Principal principal) { return access.list(id, subject(principal)); }

    @PatchMapping("/diagrams/{id}/members/{memberId}")
    DiagramMemberEntity updateMember(@PathVariable UUID id, @PathVariable UUID memberId,
                                     @RequestBody UpdateMemberRequest request, Principal principal) {
        requireVerified(principal);
        return access.updateRole(id, memberId, request.role(), subject(principal));
    }

    @DeleteMapping("/diagrams/{id}/members/{memberId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void removeMember(@PathVariable UUID id, @PathVariable UUID memberId, Principal principal) {
        requireVerified(principal);
        access.removeMember(id, memberId, subject(principal));
    }

    record UpdateMemberRequest(String role) {}

    public static String subject(Principal principal) {
        if (principal == null) throw new AccessDeniedException("Debes iniciar sesión");
        return principal.getName();
    }
    public static String displayName(Principal principal) {
        AccountPrincipal account = account(principal);
        if (account != null) return account.fullName();
        return principal == null ? "" : principal.getName();
    }
    public static void requireVerified(Principal principal) {
        AccountPrincipal account = account(principal);
        if (account == null || !account.verified())
            throw new AccessDeniedException("Verifica tu correo antes de editar.");
    }
    private static AccountPrincipal account(Principal principal) {
        if (principal instanceof AccountPrincipal value) return value;
        if (principal instanceof Authentication authentication && authentication.getPrincipal() instanceof AccountPrincipal value) return value;
        return null;
    }
}
