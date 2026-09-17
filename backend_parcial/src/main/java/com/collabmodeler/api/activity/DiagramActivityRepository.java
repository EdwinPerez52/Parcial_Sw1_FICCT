package com.collabmodeler.api.activity;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface DiagramActivityRepository extends JpaRepository<DiagramActivityEntity, UUID> {
    List<DiagramActivityEntity> findByDiagramIdOrderByCreatedAtDesc(UUID diagramId, Pageable pageable);
}
