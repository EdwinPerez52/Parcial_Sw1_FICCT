package com.collabmodeler.api.ai;

import com.collabmodeler.api.access.AccessService;
import com.collabmodeler.api.diagram.DiagramDocument;
import com.collabmodeler.api.diagram.DiagramEntity;
import com.collabmodeler.api.diagram.DiagramOperationRepository;
import com.collabmodeler.api.diagram.DiagramOperationRequest;
import com.collabmodeler.api.diagram.DiagramRepository;
import com.collabmodeler.api.diagram.DiagramService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@EnabledIfEnvironmentVariable(named = "AI_LIVE_TEST", matches = "true")
class OpenAiTextCommandProviderLiveTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void returnsAnApplicableStructuredOperationFromTheConfiguredProvider() {
        DiagramDocument diagram = emptyDiagram();

        DiagramOperationRequest operation = provider().interpret(
            "Crea una clase Cliente en la posición 100, 100", diagram);

        assertThat(operation.type()).isEqualTo("CLASS_CREATED");
        assertThat(operation.baseRevision()).isZero();
        assertThat(operation.expectedElementVersion()).isNull();
        DiagramDocument.ClassElement created = mapper.convertValue(
            operation.payload(), DiagramDocument.ClassElement.class);
        assertThat(created.id()).isNotNull();
        assertThat(created.name()).isEqualTo("Cliente");
        assertThat(created.attributes()).allSatisfy(attribute -> {
            assertThat(attribute.id()).isNotNull();
            assertThat(attribute.name()).isNotBlank();
            assertThat(attribute.type()).isNotBlank();
        });
        assertThat(created.position()).isEqualTo(new DiagramDocument.Position(100, 100));
    }

    @Test
    void generatesASmallPharmacyDomainWithValidRelationships() throws Exception {
        DiagramDocument current = emptyDiagram();
        DiagramOperationRequest batch = provider().interpret(
            "Genera un diagrama breve de base de datos para una farmacia con sus tablas, atributos, " +
                "relaciones principales y cardinalidades", current);

        assertThat(batch.type()).isEqualTo("BATCH");
        List<DiagramOperationRequest> operations = StreamSupport.stream(
                batch.payload().path("operations").spliterator(), false)
            .map(value -> mapper.convertValue(value, DiagramOperationRequest.class))
            .toList();
        List<DiagramOperationRequest> classes = operations.stream()
            .filter(value -> "CLASS_CREATED".equals(value.type())).toList();
        List<DiagramOperationRequest> associations = operations.stream()
            .filter(value -> "ASSOCIATION_CREATED".equals(value.type())).toList();

        assertThat(classes).hasSizeBetween(4, 8);
        assertThat(classes).allSatisfy(value -> {
            assertThat(value.payload().path("name").asText()).isNotBlank();
            assertThat(value.payload().path("attributes").isArray()).isTrue();
            assertThat(value.payload().path("attributes")).isNotEmpty();
        });
        assertThat(associations).hasSizeGreaterThanOrEqualTo(3);
        Set<String> classIds = classes.stream().map(value -> value.payload().path("id").asText())
            .collect(java.util.stream.Collectors.toSet());
        assertThat(associations).allSatisfy(value -> {
            assertThat(classIds).contains(value.payload().path("sourceId").asText());
            assertThat(classIds).contains(value.payload().path("targetId").asText());
            assertThat(value.payload().path("sourceCardinality").asText()).isNotBlank();
            assertThat(value.payload().path("targetCardinality").asText()).isNotBlank();
        });

        DiagramRepository diagrams = mock(DiagramRepository.class);
        DiagramOperationRepository history = mock(DiagramOperationRepository.class);
        when(diagrams.findById(current.id())).thenReturn(Optional.of(new DiagramEntity(
            current.id(), current.name(), mapper.writeValueAsString(current), "live-test")));
        DiagramDocument preview = new DiagramService(diagrams, history, mapper, mock(AccessService.class))
            .preview(current.id(), batch);
        assertThat(preview.revision()).isEqualTo(1);
        assertThat(preview.classes()).hasSize(classes.size());
        assertThat(preview.associations()).hasSize(associations.size());
    }

    private OpenAiTextCommandProvider provider() {
        AiProperties properties = new AiProperties();
        properties.setProvider(required("AI_PROVIDER"));
        properties.setBaseUrl(required("AI_BASE_URL"));
        properties.setApiKey(required("AI_API_KEY"));
        properties.setTextModel(required("AI_TEXT_MODEL"));
        return new OpenAiTextCommandProvider(properties, mapper, RestClient.builder());
    }

    private DiagramDocument emptyDiagram() {
        return new DiagramDocument(
            UUID.randomUUID(), "Prueba en vivo", 0, List.of(), List.of(), List.of(), List.of(), List.of());
    }

    private String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalStateException(name + " no está configurada");
        return value;
    }
}
