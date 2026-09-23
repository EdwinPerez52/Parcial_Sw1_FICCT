package com.collabmodeler.api.version;

import com.collabmodeler.api.diagram.*;
import com.collabmodeler.api.support.ConflictException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DiagramVersionServiceTest {
    @Mock DiagramVersionRepository versions; @Mock DiagramRepository diagrams; @Mock DiagramOperationRepository operations;

    @Test void restoreKeepsEnumerationsAndGeneralizationsAndCreatesRevision() throws Exception {
        ObjectMapper mapper = new ObjectMapper(); UUID id = UUID.randomUUID(); UUID classA = UUID.randomUUID(); UUID classB = UUID.randomUUID();
        var snapshot = new DiagramDocument(id, "Modelo", 3,
            List.of(new DiagramDocument.ClassElement(classA, "Base", List.of(), new DiagramDocument.Position(0, 0), 1),
                new DiagramDocument.ClassElement(classB, "Hija", List.of(), new DiagramDocument.Position(1, 1), 1)),
            List.of(new DiagramDocument.Enumeration(UUID.randomUUID(), "Estado", List.of(), new DiagramDocument.Position(2, 2), 1)),
            List.of(), List.of(new DiagramDocument.Generalization(UUID.randomUUID(), classA, classB, 1)));
        var version = new DiagramVersionEntity(id, 3, "Hito", mapper.writeValueAsString(snapshot), "u", "User");
        var current = new DiagramEntity(id, "Modelo", mapper.writeValueAsString(new DiagramDocument(id, "Modelo", 0, null, null)), "u");
        current.updateModel(7, mapper.writeValueAsString(snapshot));
        when(versions.findById(version.getId())).thenReturn(Optional.of(version)); when(diagrams.findForUpdate(id)).thenReturn(Optional.of(current));
        var result = new DiagramVersionService(versions, diagrams, operations, mapper).restore(id, version.getId(), 7, "u", "User");
        assertEquals(8, result.revision()); assertEquals(1, result.enumerations().size()); assertEquals(1, result.generalizations().size());
        verify(operations).save(any());
    }

    @Test void restoreRejectsAStaleRevision() throws Exception {
        ObjectMapper mapper = new ObjectMapper(); UUID id = UUID.randomUUID(); var snapshot = new DiagramDocument(id, "M", 0, null, null);
        var version = new DiagramVersionEntity(id, 0, "Hito", mapper.writeValueAsString(snapshot), "u", "U");
        var current = new DiagramEntity(id, "M", mapper.writeValueAsString(snapshot), "u"); current.updateModel(2, mapper.writeValueAsString(snapshot));
        when(versions.findById(version.getId())).thenReturn(Optional.of(version)); when(diagrams.findForUpdate(id)).thenReturn(Optional.of(current));
        assertThrows(ConflictException.class, () -> new DiagramVersionService(versions, diagrams, operations, mapper).restore(id, version.getId(), 1, "u", "U"));
    }
}
