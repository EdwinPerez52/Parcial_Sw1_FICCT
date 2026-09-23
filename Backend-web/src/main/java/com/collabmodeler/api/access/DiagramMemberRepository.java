package com.collabmodeler.api.access;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DiagramMemberRepository extends JpaRepository<DiagramMemberEntity, UUID> {
    Optional<DiagramMemberEntity> findByDiagramIdAndSubject(UUID diagramId, String subject);
    List<DiagramMemberEntity> findByDiagramIdOrderByJoinedAt(UUID diagramId);
    List<DiagramMemberEntity> findBySubjectOrderByJoinedAtDesc(String subject);
}
