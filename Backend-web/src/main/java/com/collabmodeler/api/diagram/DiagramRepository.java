package com.collabmodeler.api.diagram;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface DiagramRepository extends JpaRepository<DiagramEntity, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from DiagramEntity d where d.id = :id")
    Optional<DiagramEntity> findForUpdate(@Param("id") UUID id);
    Optional<DiagramEntity> findByShareTokenHash(String shareTokenHash);
}
