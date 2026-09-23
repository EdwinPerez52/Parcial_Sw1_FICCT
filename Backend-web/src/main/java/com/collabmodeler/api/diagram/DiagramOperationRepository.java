package com.collabmodeler.api.diagram;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface DiagramOperationRepository extends JpaRepository<DiagramOperationEntity, UUID> {
    List<DiagramOperationEntity> findByDiagramIdAndResultRevisionGreaterThanOrderByResultRevision(UUID diagramId, long revision);
}

