package com.collabmodeler.api.diagram;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DiagramDocumentJsonTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void roundTripsTheCompleteSemanticModelWithoutInformationLoss() throws Exception {
        UUID person = UUID.randomUUID();
        UUID employee = UUID.randomUUID();
        UUID status = UUID.randomUUID();
        var model = new DiagramDocument(
            UUID.randomUUID(), "Salud", 12,
            List.of(
                new DiagramDocument.ClassElement(person, "Persona", List.of(
                    new DiagramDocument.Attribute(UUID.randomUUID(), "id", "UUID", true, true, true, 3)
                ), new DiagramDocument.Position(10, 20), 4),
                new DiagramDocument.ClassElement(employee, "Medico", List.of(
                    new DiagramDocument.Attribute(UUID.randomUUID(), "estado", status.toString(), false, true, false, 2)
                ), new DiagramDocument.Position(300, 20), 5)
            ),
            List.of(new DiagramDocument.Enumeration(status, "Estado", List.of(
                new DiagramDocument.EnumerationValue(UUID.randomUUID(), "ACTIVO", 1),
                new DiagramDocument.EnumerationValue(UUID.randomUUID(), "INACTIVO", 1)
            ), new DiagramDocument.Position(600, 20), 2)),
            List.of(new DiagramDocument.Association(UUID.randomUUID(), person, employee, "1", "0..*",
                "atiende", "paciente", "medicos", "TARGET", 7)),
            List.of(new DiagramDocument.Generalization(UUID.randomUUID(), person, employee, 2)),
            List.of(new DiagramDocument.PackageElement(UUID.randomUUID(), "Dominio", null, List.of(person, employee, status), 1))
        );

        assertEquals(model, mapper.readValue(mapper.writeValueAsString(model), DiagramDocument.class));
    }

    @Test
    void readsDocumentsStoredBeforeEnumerationsAndInheritance() throws Exception {
        UUID id = UUID.randomUUID();
        String json = """
            {"id":"%s","name":"Legacy","revision":0,"classes":[],"associations":[]}
            """.formatted(id);
        DiagramDocument result = mapper.readValue(json, DiagramDocument.class);
        assertEquals(List.of(), result.enumerations());
        assertEquals(List.of(), result.generalizations());
        assertEquals(List.of(), result.packages());
    }
}
