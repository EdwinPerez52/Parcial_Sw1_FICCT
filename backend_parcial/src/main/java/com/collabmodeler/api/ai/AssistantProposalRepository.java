package com.collabmodeler.api.ai;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.util.UUID;

public interface AssistantProposalRepository extends JpaRepository<AssistantProposalEntity, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from AssistantProposalEntity p where p.id = :id")
    Optional<AssistantProposalEntity> findForUpdate(@Param("id") UUID id);
}
