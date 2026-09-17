package com.collabmodeler.api.diagram;

import com.collabmodeler.api.access.DiagramMemberEntity;
import com.collabmodeler.api.access.DiagramMemberRepository;
import com.collabmodeler.api.comment.CommentEntity;
import com.collabmodeler.api.comment.CommentRepository;
import com.collabmodeler.api.version.DiagramVersionEntity;
import com.collabmodeler.api.version.DiagramVersionRepository;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIfEnvironmentVariable(named = "TEST_DATABASE_URL", matches = ".+")
class ExternalPostgresPersistenceTest {
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("TEST_DATABASE_URL"));
        registry.add("spring.datasource.username", () -> System.getenv().getOrDefault("TEST_DATABASE_USER", "modeler"));
        registry.add("spring.datasource.password", () -> System.getenv().getOrDefault("TEST_DATABASE_PASSWORD", ""));
    }

    @Autowired DiagramRepository diagrams;
    @Autowired DiagramOperationRepository operations;
    @Autowired DiagramMemberRepository members;
    @Autowired CommentRepository comments;
    @Autowired DiagramVersionRepository versions;
    @Autowired Flyway flyway;

    @Test
    void cleanPostgresMigratesAndPersistsTheWholeAggregate() {
        assertEquals("6", flyway.info().current().getVersion().getVersion());
        UUID diagramId = UUID.randomUUID();
        var diagram = new DiagramEntity(diagramId, "Salud",
            "{\"id\":\"%s\",\"name\":\"Salud\",\"revision\":0,\"classes\":[],\"enumerations\":[],\"associations\":[],\"generalizations\":[]}".formatted(diagramId),
            "owner");
        diagrams.saveAndFlush(diagram);
        members.saveAndFlush(new DiagramMemberEntity(diagramId, "owner", "Owner", "OWNER"));
        operations.saveAndFlush(new DiagramOperationEntity(UUID.randomUUID(), diagramId, 0, 1,
            "CLASS_CREATED", "{}", "owner", "Owner"));
        comments.saveAndFlush(new CommentEntity(diagramId, "DIAGRAM", null, "Persistente", "owner", "Owner"));
        versions.saveAndFlush(new DiagramVersionEntity(diagramId, 1, "Inicial", diagram.getModelJson(), "owner", "Owner"));

        assertTrue(diagrams.findById(diagramId).isPresent());
        assertEquals(1, operations.findByDiagramIdAndResultRevisionGreaterThanOrderByResultRevision(diagramId, 0).size());
        assertEquals(1, members.findByDiagramIdOrderByJoinedAt(diagramId).size());
        assertEquals(1, comments.findByDiagramIdOrderByCreatedAt(diagramId).size());
        assertEquals(1, versions.findByDiagramIdOrderByCreatedAtDesc(diagramId).size());
    }
}
