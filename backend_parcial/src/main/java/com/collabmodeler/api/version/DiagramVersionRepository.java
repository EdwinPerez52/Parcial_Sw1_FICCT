package com.collabmodeler.api.version;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;
public interface DiagramVersionRepository extends JpaRepository<DiagramVersionEntity, UUID> {
    List<DiagramVersionEntity> findByDiagramIdOrderByCreatedAtDesc(UUID diagramId);
    java.util.Optional<DiagramVersionEntity> findFirstByDiagramIdAndSourceRevisionOrderByCreatedAtAsc(UUID diagramId, long sourceRevision);
}
