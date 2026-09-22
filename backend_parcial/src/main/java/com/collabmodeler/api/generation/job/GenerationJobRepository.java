package com.collabmodeler.api.generation.job;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;

public interface GenerationJobRepository extends JpaRepository<GenerationJobEntity, UUID> {
    Optional<GenerationJobEntity> findByDiagramIdAndRequesterSubjectAndIdempotencyKey(UUID diagramId, String requesterSubject, String idempotencyKey);
    List<GenerationJobEntity> findByDiagramIdOrderByQueuedAtDesc(UUID diagramId);
    Optional<GenerationJobEntity> findByIdAndDiagramId(UUID id, UUID diagramId);
    List<GenerationJobEntity> findByExpiresAtBefore(java.time.Instant instant);
    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select j from GenerationJobEntity j where j.id = :id")
    Optional<GenerationJobEntity> findForUpdate(@Param("id") UUID id);
}
