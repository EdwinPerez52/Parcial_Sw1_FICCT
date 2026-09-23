package com.collabmodeler.api.access;

import com.collabmodeler.api.diagram.DiagramEntity;
import com.collabmodeler.api.diagram.DiagramRepository;
import com.collabmodeler.api.support.NotFoundException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Set;

@Service
public class AccessService {
    private final DiagramRepository diagrams;
    private final DiagramMemberRepository members;
    private final SecureRandom random = new SecureRandom();

    public AccessService(DiagramRepository diagrams, DiagramMemberRepository members) {
        this.diagrams = diagrams; this.members = members;
    }

    public void addOwner(UUID diagramId, String subject, String displayName) {
        DiagramMemberEntity member = new DiagramMemberEntity(diagramId, subject, displayName, "OWNER");
        if (subject.startsWith("account:")) {
            try { member.setAccountId(UUID.fromString(subject.substring("account:".length()))); } catch (IllegalArgumentException ignored) {}
        }
        members.save(member);
    }

    public void requireMember(UUID diagramId, String subject) {
        var membership = members.findByDiagramIdAndSubject(diagramId, subject);
        if (membership.isEmpty() || "PENDING".equals(membership.get().getRole())) {
            throw new AccessDeniedException("No tienes acceso a este diagrama");
        }
    }

    public void requireEditor(UUID diagramId, String subject) {
        var membership = members.findByDiagramIdAndSubject(diagramId, subject)
            .orElseThrow(() -> new AccessDeniedException("No tienes acceso a este diagrama"));
        if (!"OWNER".equals(membership.getRole()) && !"EDITOR".equals(membership.getRole())) {
            throw new AccessDeniedException("Tu rol no permite modificar este diagrama");
        }
    }

    public void requireOwner(UUID diagramId, String subject) {
        var membership = members.findByDiagramIdAndSubject(diagramId, subject)
            .orElseThrow(() -> new AccessDeniedException("No tienes acceso a este diagrama"));
        if (!"OWNER".equals(membership.getRole())) throw new AccessDeniedException("Solo el propietario puede administrar el enlace");
    }

    @Transactional
    public Map<String, String> rotateLink(UUID diagramId, String subject) {
        requireOwner(diagramId, subject);
        DiagramEntity entity = diagrams.findById(diagramId).orElseThrow(() -> new NotFoundException("Diagrama no encontrado"));
        byte[] bytes = new byte[24]; random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        entity.setShareTokenHash(hash(token));
        return Map.of("token", token, "path", "/join/" + token);
    }

    @Transactional
    public void revokeLink(UUID diagramId, String subject) {
        requireOwner(diagramId, subject);
        DiagramEntity entity = diagrams.findById(diagramId).orElseThrow(() -> new NotFoundException("Diagrama no encontrado"));
        entity.setShareTokenHash(null);
    }

    @Transactional
    public UUID join(String token, String subject, String displayName) {
        DiagramEntity entity = diagrams.findByShareTokenHash(hash(token))
            .orElseThrow(() -> new NotFoundException("El enlace no existe o fue revocado"));
        members.findByDiagramIdAndSubject(entity.getId(), subject)
            .orElseGet(() -> {
                DiagramMemberEntity member = new DiagramMemberEntity(entity.getId(), subject, displayName, "EDITOR");
                if (subject.startsWith("account:")) {
                    try { member.setAccountId(UUID.fromString(subject.substring("account:".length()))); } catch (IllegalArgumentException ignored) {}
                }
                return members.save(member);
            });
        return entity.getId();
    }

    public List<DiagramMemberEntity> list(UUID diagramId, String subject) {
        requireMember(diagramId, subject);
        return members.findByDiagramIdOrderByJoinedAt(diagramId);
    }

    @Transactional
    public DiagramMemberEntity updateRole(UUID diagramId, UUID memberId, String role, String subject) {
        requireOwner(diagramId, subject);
        if (!Set.of("EDITOR", "READER").contains(role)) {
            throw new IllegalArgumentException("El rol debe ser EDITOR o READER");
        }
        DiagramMemberEntity member = members.findById(memberId)
            .filter(value -> value.getDiagramId().equals(diagramId))
            .orElseThrow(() -> new NotFoundException("Miembro no encontrado"));
        if ("OWNER".equals(member.getRole())) throw new IllegalArgumentException("No se puede cambiar el rol del propietario");
        member.setRole(role);
        return members.save(member);
    }

    @Transactional
    public void removeMember(UUID diagramId, UUID memberId, String subject) {
        requireOwner(diagramId, subject);
        DiagramMemberEntity member = members.findById(memberId)
            .filter(value -> value.getDiagramId().equals(diagramId))
            .orElseThrow(() -> new NotFoundException("Miembro no encontrado"));
        if ("OWNER".equals(member.getRole())) throw new IllegalArgumentException("No se puede eliminar al propietario");
        members.delete(member);
    }

    public List<ProjectSummary> projects(String subject) {
        return members.findBySubjectOrderByJoinedAtDesc(subject).stream()
            .filter(member -> !"PENDING".equals(member.getRole()))
            .map(member -> diagrams.findById(member.getDiagramId())
                .map(diagram -> new ProjectSummary(diagram.getId(), diagram.getName(), member.getRole(), diagram.getRevision(), diagram.getUpdatedAt()))
                .orElse(null))
            .filter(java.util.Objects::nonNull).toList();
    }

    public record ProjectSummary(UUID id, String name, String role, long revision, java.time.Instant updatedAt) {}

    private String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(digest);
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
