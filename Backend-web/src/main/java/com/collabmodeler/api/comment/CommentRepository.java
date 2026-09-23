package com.collabmodeler.api.comment;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;
public interface CommentRepository extends JpaRepository<CommentEntity, UUID> {
    List<CommentEntity> findByDiagramIdOrderByCreatedAt(UUID diagramId);
}
