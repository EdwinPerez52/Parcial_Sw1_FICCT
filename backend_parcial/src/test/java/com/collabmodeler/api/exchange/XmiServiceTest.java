package com.collabmodeler.api.exchange;

import com.collabmodeler.api.diagram.DiagramDocument;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class XmiServiceTest {
    private final XmiService service = new XmiService();

    @Test
    void roundTripsTheCompleteSemanticModel() {
        UUID personId = UUID.randomUUID();
        UUID employeeId = UUID.randomUUID();
        UUID statusId = UUID.randomUUID();
        UUID associationId = UUID.randomUUID();
        UUID packageId = UUID.randomUUID();
        var person = new DiagramDocument.ClassElement(personId, "Persona",
            List.of(new DiagramDocument.Attribute(UUID.randomUUID(), "id", "UUID", true, true, true)),
            new DiagramDocument.Position(0, 0), 1);
        var employee = new DiagramDocument.ClassElement(employeeId, "Empleado",
            List.of(new DiagramDocument.Attribute(UUID.randomUUID(), "estado", "Estado", false, true, false)),
            new DiagramDocument.Position(100, 0), 1);
        var enumeration = new DiagramDocument.Enumeration(statusId, "Estado", List.of(
            new DiagramDocument.EnumerationValue(UUID.randomUUID(), "ACTIVO", 1),
            new DiagramDocument.EnumerationValue(UUID.randomUUID(), "INACTIVO", 1)
        ), new DiagramDocument.Position(200, 0), 1);
        var association = new DiagramDocument.Association(associationId, personId, employeeId,
            "1", "0..*", "empleos", "persona", "empleados", "TARGET", 1);
        var model = new DiagramDocument(UUID.randomUUID(), "RecursosHumanos", 2,
            List.of(person, employee), List.of(enumeration), List.of(association),
            List.of(new DiagramDocument.Generalization(UUID.randomUUID(), personId, employeeId, 1)),
            List.of(new DiagramDocument.PackageElement(packageId, "Dominio", null,
                List.of(personId, employeeId, statusId, associationId), 1)));

        String xml = service.exportXmi(model);
        var imported = service.importXmi(xml.getBytes(StandardCharsets.UTF_8), "fallback");

        String firstAttribute = xml.substring(xml.indexOf("<ownedAttribute"), xml.indexOf("</ownedAttribute>") + "</ownedAttribute>".length());
        assertFalse(firstAttribute.contains("isUnique="));
        assertFalse(firstAttribute.contains("lowerValue"));
        assertTrue(firstAttribute.contains("collab:primaryKey=\"true\""));
        assertTrue(firstAttribute.contains("collab:required=\"true\""));

        assertEquals("RecursosHumanos", imported.diagram().name());
        assertEquals(Set.of("Persona", "Empleado"), imported.diagram().classes().stream().map(DiagramDocument.ClassElement::name).collect(Collectors.toSet()));
        assertEquals(List.of("ACTIVO", "INACTIVO"), imported.diagram().enumerations().getFirst().values().stream().map(DiagramDocument.EnumerationValue::name).toList());
        assertEquals("Estado", imported.diagram().classes().stream().filter(value -> value.name().equals("Empleado")).findFirst().orElseThrow().attributes().getFirst().type());
        assertTrue(imported.diagram().classes().stream().filter(value -> value.name().equals("Persona")).findFirst().orElseThrow().attributes().getFirst().unique());
        assertFalse(imported.diagram().classes().stream().filter(value -> value.name().equals("Empleado")).findFirst().orElseThrow().attributes().getFirst().unique());
        assertEquals("TARGET", imported.diagram().associations().getFirst().owningSide());
        assertEquals("persona", imported.diagram().associations().getFirst().sourceRole());
        assertEquals("empleados", imported.diagram().associations().getFirst().targetRole());
        assertEquals(1, imported.diagram().generalizations().size());
        assertEquals("Dominio", imported.diagram().packages().getFirst().name());
        assertEquals(4, imported.diagram().packages().getFirst().memberIds().size());
        assertTrue(imported.warnings().isEmpty());
    }

    @Test
    void importsEnterpriseArchitectStyleOwnedAndClassOwnedAssociationEnds() {
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <xmi:XMI xmi:version="2.1" xmlns:xmi="http://schema.omg.org/spec/XMI/2.1" xmlns:uml="http://schema.omg.org/spec/UML/2.1">
              <uml:Model xmi:type="uml:Model" xmi:id="EA_Model" name="VentasEA">
                <packagedElement xmi:type="uml:Package" xmi:id="EA_Package" name="Ventas">
                  <packagedElement xmi:type="uml:Enumeration" xmi:id="EA_Status" name="EstadoPedido">
                    <ownedLiteral xmi:type="uml:EnumerationLiteral" xmi:id="EA_New" name="NUEVO"/>
                  </packagedElement>
                  <packagedElement xmi:type="uml:Class" xmi:id="EA_Customer" name="Cliente">
                    <ownedAttribute xmi:type="uml:Property" xmi:id="EA_CustomerId" name="id" type="EA_UUID" isID="true" isUnique="true">
                      <lowerValue xmi:type="uml:LiteralInteger" xmi:id="lv1" value="1"/>
                      <upperValue xmi:type="uml:LiteralUnlimitedNatural" xmi:id="uv1" value="1"/>
                    </ownedAttribute>
                    <ownedAttribute xmi:type="uml:Property" xmi:id="EA_CustomerOrders" name="pedidos" type="EA_Order" association="EA_Association">
                      <lowerValue xmi:type="uml:LiteralInteger" xmi:id="lv2" value="0"/>
                      <upperValue xmi:type="uml:LiteralUnlimitedNatural" xmi:id="uv2" value="*"/>
                    </ownedAttribute>
                  </packagedElement>
                  <packagedElement xmi:type="uml:Class" xmi:id="EA_Order" name="Pedido">
                    <ownedAttribute xmi:type="uml:Property" xmi:id="EA_OrderStatus" name="estado" type="EA_Status"/>
                    <generalization xmi:type="uml:Generalization" xmi:id="EA_Generalization" general="EA_Customer"/>
                  </packagedElement>
                  <packagedElement xmi:type="uml:Association" xmi:id="EA_Association" name="realiza" memberEnd="EA_OrderCustomer EA_CustomerOrders" navigableOwnedEnd="EA_CustomerOrders">
                    <ownedEnd xmi:type="uml:Property" xmi:id="EA_OrderCustomer" name="cliente" type="EA_Customer">
                      <lowerValue xmi:type="uml:LiteralInteger" xmi:id="lv3" value="1"/>
                      <upperValue xmi:type="uml:LiteralUnlimitedNatural" xmi:id="uv3" value="1"/>
                    </ownedEnd>
                  </packagedElement>
                  <packagedElement xmi:type="uml:PrimitiveType" xmi:id="EA_UUID" name="UUID"/>
                  <packagedElement xmi:type="uml:StateMachine" xmi:id="EA_Unsupported" name="Flujo"/>
                </packagedElement>
              </uml:Model>
              <xmi:Extension extender="Enterprise Architect"><elements/></xmi:Extension>
            </xmi:XMI>
            """;

        var imported = service.importXmi(xml.getBytes(StandardCharsets.UTF_8), "fallback");

        assertEquals(2, imported.diagram().classes().size());
        assertEquals("UUID", imported.diagram().classes().stream().filter(value -> value.name().equals("Cliente")).findFirst().orElseThrow().attributes().getFirst().type());
        assertEquals("EstadoPedido", imported.diagram().classes().stream().filter(value -> value.name().equals("Pedido")).findFirst().orElseThrow().attributes().getFirst().type());
        assertEquals("1", imported.diagram().associations().getFirst().sourceCardinality());
        assertEquals("0..*", imported.diagram().associations().getFirst().targetCardinality());
        assertEquals("TARGET", imported.diagram().associations().getFirst().owningSide());
        assertEquals(1, imported.diagram().generalizations().size());
        assertTrue(imported.warnings().stream().anyMatch(value -> value.code().equals("UNSUPPORTED_ELEMENT") && "EA_Unsupported".equals(value.externalId())));
        assertTrue(imported.warnings().stream().anyMatch(value -> value.code().equals("EA_EXTENSION_IGNORED")));
    }

    @Test
    void importsEnterpriseArchitectNativePackageXml() {
        String xml = """
            <?xml version="1.0" encoding="windows-1252"?>
            <Package name="VideoClub_AlquilerPeliculas" guid="{PKG}">
              <Table name="t_object">
                <Row><Column name="Object_ID" value="17"/><Column name="Object_Type" value="Class"/><Column name="Name" value="director"/><Column name="ea_guid" value="{DIRECTOR}"/></Row>
                <Row><Column name="Object_ID" value="18"/><Column name="Object_Type" value="Class"/><Column name="Name" value="pelicula"/><Column name="ea_guid" value="{PELICULA}"/></Row>
              </Table>
              <Table name="t_attribute">
                <Row><Column name="Object_ID" value="17"/><Column name="ID" value="53"/><Column name="Name" value="id"/><Column name="Type" value="int"/><Column name="LowerBound" value="1"/><Column name="ea_guid" value="{ATTR}"/></Row>
              </Table>
              <Table name="t_connector">
                <Row><Column name="Connector_ID" value="25"/><Column name="Connector_Type" value="Association"/><Column name="Start_Object_ID" value="17"/><Column name="End_Object_ID" value="18"/><Column name="SourceCard" value="1..1"/><Column name="DestCard" value="0..*"/><Column name="ea_guid" value="{CONN}"/></Row>
              </Table>
              <Table name="t_diagramobjects">
                <Row><Column name="Object_ID" value="17"/><Column name="RectLeft" value="220"/><Column name="RectTop" value="-180"/></Row>
              </Table>
            </Package>
            """;

        var imported = service.importXmi(xml.getBytes(StandardCharsets.ISO_8859_1), "prueba");

        assertEquals("VideoClub_AlquilerPeliculas", imported.diagram().name());
        assertEquals(Set.of("director", "pelicula"), imported.diagram().classes().stream()
            .map(DiagramDocument.ClassElement::name).collect(Collectors.toSet()));
        assertEquals("Integer", imported.diagram().classes().stream().filter(value -> value.name().equals("director"))
            .findFirst().orElseThrow().attributes().getFirst().type());
        assertEquals("1", imported.diagram().associations().getFirst().sourceCardinality());
        assertEquals("0..*", imported.diagram().associations().getFirst().targetCardinality());
        assertEquals(220, imported.diagram().classes().stream().filter(value -> value.name().equals("director"))
            .findFirst().orElseThrow().position().x());
        assertTrue(imported.warnings().stream().anyMatch(value -> value.code().equals("EA_NATIVE_XML_IMPORTED")));
    }

    @Test
    void roundTripsSeveralRepresentativeEnterpriseArchitectFilesSemantically() throws Exception {
        for (String resource : List.of("ea-sales.xmi", "ea-school.xmi", "ea-health.xmi")) {
            byte[] bytes;
            try (InputStream input = getClass().getResourceAsStream("/xmi/" + resource)) {
                assertNotNull(input, resource);
                bytes = input.readAllBytes();
            }
            DiagramDocument original = service.importXmi(bytes, resource).diagram();
            DiagramDocument reimported = service.importXmi(
                service.exportXmi(original).getBytes(StandardCharsets.UTF_8), resource).diagram();
            assertEquals(semanticSignature(original), semanticSignature(reimported), resource);
        }
    }

    @Test
    void rejectsDoctypeOversizeWrongVersionAndNonXmiContent() {
        String malicious = "<!DOCTYPE x [<!ENTITY e SYSTEM 'file:///etc/passwd'>]><x>&e;</x>";
        assertThrows(IllegalArgumentException.class, () -> service.importXmi(malicious.getBytes(StandardCharsets.UTF_8), "x"));
        assertThrows(IllegalArgumentException.class, () -> service.importXmi(new byte[XmiService.MAX_BYTES + 1], "x"));
        assertThrows(IllegalArgumentException.class, () -> service.importXmi("<x/>".getBytes(StandardCharsets.UTF_8), "x"));
        assertThrows(IllegalArgumentException.class, () -> service.importXmi("<xmi:XMI xmlns:xmi='urn:xmi' xmi:version='2.5'/>".getBytes(StandardCharsets.UTF_8), "x"));
    }

    private List<String> semanticSignature(DiagramDocument diagram) {
        List<String> result = new ArrayList<>();
        Map<UUID, String> names = new HashMap<>();
        diagram.classes().forEach(value -> names.put(value.id(), value.name()));
        diagram.enumerations().forEach(value -> names.put(value.id(), value.name()));
        diagram.associations().forEach(value -> names.put(value.id(), "association:" + value.name()));
        diagram.classes().forEach(value -> result.add("C:" + value.name() + ":" + value.attributes().stream()
            .map(attribute -> attribute.name() + ":" + attribute.type() + ":" + attribute.primaryKey() + ":" + attribute.required() + ":" + attribute.unique()).toList()));
        diagram.enumerations().forEach(value -> result.add("E:" + value.name() + ":" + value.values().stream().map(DiagramDocument.EnumerationValue::name).toList()));
        diagram.associations().forEach(value -> result.add("A:" + names.get(value.sourceId()) + ":" + names.get(value.targetId()) + ":"
            + value.sourceCardinality() + ":" + value.targetCardinality() + ":" + value.name() + ":" + value.sourceRole() + ":" + value.targetRole() + ":" + value.owningSide()));
        diagram.generalizations().forEach(value -> result.add("G:" + names.get(value.parentId()) + ":" + names.get(value.childId())));
        Map<UUID, String> packageNames = diagram.packages().stream().collect(Collectors.toMap(DiagramDocument.PackageElement::id, DiagramDocument.PackageElement::name));
        diagram.packages().forEach(value -> result.add("P:" + value.name() + ":" + packageNames.get(value.parentId()) + ":"
            + value.memberIds().stream().map(names::get).sorted(Comparator.nullsFirst(String::compareTo)).toList()));
        return result.stream().sorted().toList();
    }
}
