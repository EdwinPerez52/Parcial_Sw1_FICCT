package com.collabmodeler.api.version;

import com.collabmodeler.api.diagram.*;
import com.collabmodeler.api.support.ConflictException;
import com.collabmodeler.api.support.NotFoundException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.UUID;

@Service
public class DiagramVersionService {
    private final DiagramVersionRepository versions; private final DiagramRepository diagrams;
    private final DiagramOperationRepository operations; private final ObjectMapper mapper;
    public DiagramVersionService(DiagramVersionRepository versions, DiagramRepository diagrams,
                                 DiagramOperationRepository operations, ObjectMapper mapper) {
        this.versions = versions; this.diagrams = diagrams; this.operations = operations; this.mapper = mapper;
    }
    @Transactional
    public DiagramVersionEntity create(UUID diagramId, String label, String subject, String name) {
        if (label == null || label.isBlank() || label.trim().length() > 180) throw new IllegalArgumentException("El nombre del hito admite entre 1 y 180 caracteres");
        DiagramEntity entity = diagrams.findById(diagramId).orElseThrow(() -> new NotFoundException("Diagrama no encontrado"));
        return versions.save(new DiagramVersionEntity(diagramId, entity.getRevision(), label.trim(), entity.getModelJson(), subject, name));
    }
    public List<DiagramVersionEntity> list(UUID diagramId) { return versions.findByDiagramIdOrderByCreatedAtDesc(diagramId); }
    public DiagramDocument preview(UUID diagramId, UUID versionId) {
        try { return mapper.readValue(find(diagramId, versionId).getSnapshotJson(), DiagramDocument.class); }
        catch (Exception exception) { throw new IllegalStateException("No se pudo leer la versión", exception); }
    }
    @Transactional
    public DiagramDocument restore(UUID diagramId, UUID versionId, long expectedRevision, String subject, String name) {
        DiagramVersionEntity version = find(diagramId, versionId);
        DiagramEntity entity = diagrams.findForUpdate(diagramId).orElseThrow(() -> new NotFoundException("Diagrama no encontrado"));
        if (entity.getRevision() != expectedRevision) throw new ConflictException("RESTORE_REVISION_MISMATCH",
            "El diagrama cambió antes de restaurar el hito", entity.getRevision(), null, null);
        try {
            DiagramDocument snapshot = mapper.readValue(version.getSnapshotJson(), DiagramDocument.class);
            long next = entity.getRevision() + 1;
            DiagramDocument restored = new DiagramDocument(diagramId, entity.getName(), next, snapshot.classes(),
                snapshot.enumerations(), snapshot.associations(), snapshot.generalizations());
            entity.updateModel(next, mapper.writeValueAsString(restored));
            var payload = mapper.createObjectNode().put("versionId", versionId.toString());
            payload.set("model", mapper.valueToTree(restored));
            operations.save(new DiagramOperationEntity(UUID.randomUUID(), diagramId, next - 1, next, "MODEL_RESTORED",
                payload.toString(), subject, name));
            return restored;
        } catch (ConflictException exception) { throw exception; }
        catch (Exception exception) { throw new IllegalStateException("No se pudo restaurar la versión", exception); }
    }
    private DiagramVersionEntity find(UUID diagramId, UUID versionId) {
        return versions.findById(versionId).filter(item -> item.getDiagramId().equals(diagramId))
            .orElseThrow(() -> new NotFoundException("Versión no encontrada"));
    }
}
