package com.collabmodeler.api.ai;

import com.collabmodeler.api.diagram.DiagramDocument;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withUnauthorizedRequest;

class OpenAiTextCommandProviderTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final UUID classId = UUID.randomUUID();
    private final UUID attributeId = UUID.randomUUID();

    @Test void preservesOwnerVersionWhenAiAddsAnAttribute() {
        String createdId = UUID.randomUUID().toString();
        String arguments = """
            {"expectedElementVersion":3,"payload":{"classId":"%s","attribute":{"id":"%s","name":"correo","type":"String","primaryKey":false,"required":true,"unique":true,"version":1}}}
            """.formatted(classId, createdId).trim();
        var fixture = fixture(toolResponse("ATTRIBUTE_CREATED", arguments));

        var operation = fixture.provider.interpret("agrega un correo único y obligatorio a Cliente", diagram());

        assertEquals("ATTRIBUTE_CREATED", operation.type());
        assertEquals(3L, operation.expectedElementVersion());
        assertEquals(classId.toString(), operation.payload().path("classId").asText());
        assertEquals("correo", operation.payload().path("attribute").path("name").asText());
        assertNotEquals(createdId, operation.payload().path("attribute").path("id").asText());
        fixture.server.verify();
    }

    @Test void producesApplicableUpdateAndDeleteOperations() {
        String arguments = """
            {"expectedElementVersion":2,"payload":{"classId":"%s","attribute":{"id":"%s","name":"email","type":"String","primaryKey":false,"required":true,"unique":true,"version":2}}}
            """.formatted(classId, attributeId).trim();
        var fixture = fixture(toolResponse("ATTRIBUTE_UPDATED", arguments));

        var update = fixture.provider.interpret("renombra correo a email y hazlo obligatorio", diagram());

        assertEquals("ATTRIBUTE_UPDATED", update.type());
        assertEquals(2L, update.expectedElementVersion());
        assertEquals(attributeId.toString(), update.payload().path("attribute").path("id").asText());
        fixture.server.verify();

        String deleteArguments = """
            {"expectedElementVersion":2,"payload":{"classId":"%s","id":"%s"}}
            """.formatted(classId, attributeId).trim();
        var deleteFixture = fixture(toolResponse("ATTRIBUTE_DELETED", deleteArguments));
        var deletion = deleteFixture.provider.interpret("elimina el atributo correo de Cliente", diagram());
        assertEquals("ATTRIBUTE_DELETED", deletion.type());
        assertEquals(2L, deletion.expectedElementVersion());
        deleteFixture.server.verify();
    }

    @Test void reportsRejectedCredentialsAsUnavailable() {
        var properties = properties();
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://example.test/v1/chat/completions")).andRespond(withUnauthorizedRequest());
        var provider = new OpenAiTextCommandProvider(properties, mapper, builder);

        var error = assertThrows(AiUnavailableException.class,
            () -> provider.interpret("haz un cambio complejo", diagram()));

        assertTrue(error.getMessage().contains("AI_API_KEY"));
        server.verify();
    }

    @Test void supportsPackageCrudOperations() {
        UUID packageId = UUID.randomUUID();
        String createArguments = """
            {"expectedElementVersion":null,"payload":{"id":"%s","name":"Ventas","parentId":null,"memberIds":["%s"],"version":1}}
            """.formatted(packageId, classId).trim();
        var createFixture = fixture(toolResponse("PACKAGE_CREATED", createArguments));

        var creation = createFixture.provider.interpret("crea el paquete Ventas y agrega Cliente", diagram());

        assertEquals("PACKAGE_CREATED", creation.type());
        assertNull(creation.expectedElementVersion());
        assertNotEquals(packageId.toString(), creation.payload().path("id").asText());
        assertEquals(classId.toString(), creation.payload().path("memberIds").path(0).asText());
        createFixture.server.verify();

        String updateArguments = """
            {"expectedElementVersion":2,"payload":{"id":"%s","name":"Comercial","parentId":null,"memberIds":["%s"],"version":2}}
            """.formatted(packageId, classId).trim();
        var updateFixture = fixture(toolResponse("PACKAGE_UPDATED", updateArguments));

        var update = updateFixture.provider.interpret("renombra el paquete Ventas a Comercial", diagram());

        assertEquals("PACKAGE_UPDATED", update.type());
        assertEquals(2L, update.expectedElementVersion());
        assertEquals(packageId.toString(), update.payload().path("id").asText());
        updateFixture.server.verify();

        String deleteArguments = """
            {"expectedElementVersion":2,"payload":{"id":"%s"}}
            """.formatted(packageId).trim();
        var deleteFixture = fixture(toolResponse("PACKAGE_DELETED", deleteArguments));

        var deletion = deleteFixture.provider.interpret("elimina el paquete Comercial", diagram());

        assertEquals("PACKAGE_DELETED", deletion.type());
        assertEquals(2L, deletion.expectedElementVersion());
        deleteFixture.server.verify();
    }

    private Fixture fixture(String response) {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://example.test/v1/chat/completions"))
            .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));
        return new Fixture(new OpenAiTextCommandProvider(properties(), mapper, builder), server);
    }

    private AiProperties properties() {
        var properties = new AiProperties();
        properties.setApiKey("test-key");
        properties.setBaseUrl("https://example.test/v1");
        return properties;
    }

    private String toolResponse(String type, String arguments) {
        try {
            return mapper.writeValueAsString(mapper.createObjectNode().set("choices", mapper.createArrayNode().add(
                mapper.createObjectNode().set("message", mapper.createObjectNode().set("tool_calls", mapper.createArrayNode().add(
                    mapper.createObjectNode().set("function", mapper.createObjectNode()
                        .put("name", type).put("arguments", arguments))))))));
        } catch (Exception exception) {
            throw new RuntimeException(exception);
        }
    }

    private DiagramDocument diagram() {
        var attribute = new DiagramDocument.Attribute(attributeId, "correo", "String", false, false, false, 2);
        var clazz = new DiagramDocument.ClassElement(classId, "Cliente", List.of(attribute),
            new DiagramDocument.Position(20, 30), 3);
        return new DiagramDocument(UUID.randomUUID(), "Ventas", 7, List.of(clazz), List.of(), List.of(), List.of(), List.of());
    }

    private record Fixture(OpenAiTextCommandProvider provider, MockRestServiceServer server) {}
}
