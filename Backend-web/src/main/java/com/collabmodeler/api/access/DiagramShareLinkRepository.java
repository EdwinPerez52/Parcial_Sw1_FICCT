package com.collabmodeler.api.access;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface DiagramShareLinkRepository extends JpaRepository<DiagramShareLinkEntity, UUID> {
    Optional<DiagramShareLinkEntity> findByTokenHashAndRevokedAtIsNull(String tokenHash);

    @Modifying
    @Query("update DiagramShareLinkEntity link set link.revokedAt = :revokedAt where link.diagramId = :diagramId and link.revokedAt is null")
    int revokeAll(@Param("diagramId") UUID diagramId, @Param("revokedAt") Instant revokedAt);
}
