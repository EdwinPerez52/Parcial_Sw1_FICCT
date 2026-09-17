package com.collabmodeler.api.comment;

import com.collabmodeler.api.activity.DiagramActivityService;
import com.collabmodeler.api.collaboration.CollaborationEventPublisher;
import com.collabmodeler.api.diagram.DiagramDocument;
import com.collabmodeler.api.diagram.DiagramService;
import com.collabmodeler.api.support.NotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CommentServiceTest {
    @Mock CommentRepository comments; @Mock DiagramService diagrams;
    @Mock CollaborationEventPublisher events; @Mock DiagramActivityService activity;

    @Test void createsAReplyInTheSameElementContextAndBroadcastsIt() {
        UUID diagramId = UUID.randomUUID(); UUID classId = UUID.randomUUID();
        var model = new DiagramDocument(diagramId, "M", 0,
            List.of(new DiagramDocument.ClassElement(classId, "Producto", List.of(), new DiagramDocument.Position(0, 0), 1)), List.of());
        var root = new CommentEntity(diagramId, "CLASS", classId, "Revisar", "a", "Ana");
        when(diagrams.get(diagramId)).thenReturn(model); when(comments.findById(root.getId())).thenReturn(Optional.of(root));
        when(comments.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        CommentEntity reply = new CommentService(comments, diagrams, events, activity)
            .create(diagramId, "DIAGRAM", null, root.getId(), " De acuerdo ", "b", "Beto");
        assertEquals(root.getId(), reply.getParentCommentId()); assertEquals("CLASS", reply.getTargetType()); assertEquals(classId, reply.getTargetId());
        verify(events).publish(eq(diagramId), eq("COMMENT_CREATED"), any());
    }

    @Test void rejectsAnAnchorThatDoesNotExist() {
        UUID diagramId = UUID.randomUUID(); when(diagrams.get(diagramId)).thenReturn(new DiagramDocument(diagramId, "M", 0, null, null));
        assertThrows(NotFoundException.class, () -> new CommentService(comments, diagrams, events, activity)
            .create(diagramId, "ASSOCIATION", UUID.randomUUID(), null, "Texto", "a", "Ana"));
        verify(comments, never()).save(any());
    }
}
