package com.collabmodeler.api.exchange;

import com.collabmodeler.api.diagram.DiagramDocument;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class XmiServiceTest {
    @Test
    void roundTripsClassesAttributesAndAssociations() {
        UUID customerId = UUID.randomUUID(); UUID orderId = UUID.randomUUID();
        var customer = new DiagramDocument.ClassElement(customerId, "Cliente",
            List.of(new DiagramDocument.Attribute(UUID.randomUUID(), "id", "UUID", true, true, true)),
            new DiagramDocument.Position(0, 0), 1);
        var order = new DiagramDocument.ClassElement(orderId, "Pedido",
            List.of(new DiagramDocument.Attribute(UUID.randomUUID(), "id", "Long", true, true, true)),
            new DiagramDocument.Position(100, 0), 1);
        var model = new DiagramDocument(UUID.randomUUID(), "Ventas", 2, List.of(customer, order),
            List.of(new DiagramDocument.Association(UUID.randomUUID(), customerId, orderId, "1", "0..*", "pedidos", 1)));
        XmiService service = new XmiService();
        String xml = service.exportXmi(model);
        var imported = service.importXmi(xml.getBytes(StandardCharsets.UTF_8), "fallback");
        assertEquals("Ventas", imported.diagram().name());
        assertEquals(2, imported.diagram().classes().size());
        assertEquals(1, imported.diagram().associations().size());
        assertTrue(imported.warnings().isEmpty());
    }

    @Test
    void rejectsDoctype() {
        String malicious = "<!DOCTYPE x [<!ENTITY e SYSTEM 'file:///etc/passwd'>]><x>&e;</x>";
        assertThrows(IllegalArgumentException.class, () -> new XmiService().importXmi(malicious.getBytes(StandardCharsets.UTF_8), "x"));
    }
}
