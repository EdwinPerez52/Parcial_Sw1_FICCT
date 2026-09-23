package com.collabmodeler.api.ai;

import com.collabmodeler.api.diagram.DiagramDocument;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class LocalCommandParserTest {
    private final LocalCommandParser parser = new LocalCommandParser(new ObjectMapper());
    private final UUID personId = UUID.randomUUID();
    private final UUID orderId = UUID.randomUUID();
    private final DiagramDocument diagram = new DiagramDocument(UUID.randomUUID(), "Ventas", 7,
        List.of(
            new DiagramDocument.ClassElement(personId, "Persona", List.of(
                new DiagramDocument.Attribute(UUID.randomUUID(), "edad", "Integer", false, false, false, 2)
            ), new DiagramDocument.Position(10, 20), 3),
            new DiagramDocument.ClassElement(orderId, "Pedido", List.of(), new DiagramDocument.Position(40, 50), 1)
        ), List.of(), List.of(), List.of(), List.of());

    @Test void createsTheSameDomainOperationAsManualEditing() {
        var operation = parser.parse("crea una clase Producto", diagram).orElseThrow();
        assertEquals("CLASS_CREATED", operation.type());
        assertEquals(diagram.revision(), operation.baseRevision());
        assertEquals("Producto", operation.payload().path("name").asText());
        assertTrue(operation.payload().path("id").isTextual());
    }

    @Test void acceptsNaturalCreationVariantsWithoutCallingAi() {
        for (String instruction : List.of(
            "crea la clase Factura", "crear clase Factura", "créame una clase Factura",
            "añade una clase Factura", "genera la clase Factura")) {
            var operation = parser.parse(instruction, diagram).orElseThrow();
            assertEquals("CLASS_CREATED", operation.type(), instruction);
            assertEquals("Factura", operation.payload().path("name").asText(), instruction);
        }
    }

    @Test void supportsModificationMovementRelationsEnumerationsAndInheritance() {
        assertEquals("CLASS_RENAMED", parser.parse("renombra la clase Persona a Paciente", diagram).orElseThrow().type());
        assertEquals("CLASS_MOVED", parser.parse("mueve la clase Persona a 120, 80", diagram).orElseThrow().type());
        assertEquals("ATTRIBUTE_UPDATED", parser.parse("cambia el atributo edad de Persona a tipo Long", diagram).orElseThrow().type());
        assertEquals("ASSOCIATION_CREATED", parser.parse("relaciona Persona con Pedido", diagram).orElseThrow().type());
        assertEquals("ENUMERATION_CREATED", parser.parse("crea enumeración Estado con valores ACTIVO, INACTIVO", diagram).orElseThrow().type());
        assertEquals("GENERALIZATION_CREATED", parser.parse("Pedido hereda de Persona", diagram).orElseThrow().type());
    }

    @Test void supportsDestructiveOperationsWithoutApplyingThem() {
        var operation = parser.parse("elimina la clase Persona", diagram).orElseThrow();
        assertEquals("CLASS_DELETED", operation.type());
        assertEquals(personId.toString(), operation.payload().path("id").asText());
        assertEquals(3, operation.expectedElementVersion());
    }

    @Test void delegatesUnknownOrCompoundInstructions() {
        assertTrue(parser.parse("diseña un sistema escolar completo", diagram).isEmpty());
        assertTrue(parser.parse("crea una clase A; crea una clase B", diagram).isEmpty());
        assertTrue(parser.parse("agrega atributo correo tipo Email a Persona", diagram).isEmpty());
        assertTrue(parser.parse("agrega atributo precio en la tabla Persona", diagram).isEmpty());
    }

    @Test void normalizesCommonSqlTypesBeforeApplyingLocally() {
        assertEquals("String", parser.parse("agrega atributo nombre tipo varchar a Persona", diagram)
            .orElseThrow().payload().path("attribute").path("type").asText());
        assertEquals("DateTime", parser.parse("cambia el atributo edad de Persona a tipo timestamp", diagram)
            .orElseThrow().payload().path("attribute").path("type").asText());
    }

    @Test void addsAgeToTheNamedTableWithoutRequiringAnExplicitType() {
        UUID customerId = UUID.randomUUID();
        var customer = new DiagramDocument.ClassElement(customerId, "Cliente", List.of(),
            new DiagramDocument.Position(60, 70), 4);
        var current = new DiagramDocument(UUID.randomUUID(), "Ventas", 9,
            List.of(customer, diagram.classes().getFirst()), List.of(), List.of(), List.of(), List.of());

        var operation = parser.parse("agrega atributo edad en la tabla cliente", current).orElseThrow();

        assertEquals("ATTRIBUTE_CREATED", operation.type());
        assertEquals(current.revision(), operation.baseRevision());
        assertEquals(4L, operation.expectedElementVersion());
        assertEquals(customerId.toString(), operation.payload().path("classId").asText());
        assertEquals("edad", operation.payload().path("attribute").path("name").asText());
        assertEquals("Integer", operation.payload().path("attribute").path("type").asText());
    }
}
