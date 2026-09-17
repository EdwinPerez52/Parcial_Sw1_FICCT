package com.collabmodeler.api.diagram;

import com.collabmodeler.api.access.DiagramMemberEntity;
import com.collabmodeler.api.access.DiagramMemberRepository;
import com.collabmodeler.api.comment.CommentEntity;
import com.collabmodeler.api.comment.CommentRepository;
import com.collabmodeler.api.version.DiagramVersionEntity;
import com.collabmodeler.api.version.DiagramVersionRepository;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
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
@Testcontainers(disabledWithoutDocker = true)
class ExternalPostgresPersistenceTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired DiagramRepository diagrams;
    @Autowired DiagramOperationRepository operations;
    @Autowired DiagramMemberRepository members;
    @Autowired CommentRepository comments;
    @Autowired DiagramVersionRepository versions;
    @Autowired Flyway flyway;

    @Test
    void cleanPostgresMigratesAndPersistsTheWholeAggregate() {
        assertEquals("7", flyway.info().current().getVersion().getVersion());
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
