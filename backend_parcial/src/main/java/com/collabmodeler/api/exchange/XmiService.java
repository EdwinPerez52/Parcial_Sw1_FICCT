package com.collabmodeler.api.exchange;

import com.collabmodeler.api.diagram.DiagramDocument;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamWriter;
import java.io.ByteArrayInputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class XmiService {
    public static final int MAX_BYTES = 5_000_000;
    private static final int MAX_ELEMENTS = 20_000;
    private static final int MAX_DEPTH = 64;
    private static final String XMI = "http://schema.omg.org/spec/XMI/2.1";
    private static final String UML = "http://schema.omg.org/spec/UML/2.1";
    private static final String COLLAB = "urn:collab-modeler:xmi-metadata:1";
    private static final Pattern VALID_NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
    private static final Set<String> SUPPORTED_SCALARS = Set.of(
        "String", "Text", "Integer", "Long", "Decimal", "Boolean", "Date", "DateTime", "UUID", "Binary"
    );

    public String exportXmi(DiagramDocument diagram) {
        try {
            StringWriter output = new StringWriter();
            XMLStreamWriter xml = XMLOutputFactory.newFactory().createXMLStreamWriter(output);
            xml.writeStartDocument("UTF-8", "1.0");
            xml.writeStartElement("xmi", "XMI", XMI);
            xml.writeNamespace("xmi", XMI);
            xml.writeNamespace("uml", UML);
            xml.writeNamespace("collab", COLLAB);
            xml.writeAttribute(XMI, "version", "2.1");
            xml.writeStartElement("uml", "Model", UML);
            xml.writeAttribute(XMI, "type", "uml:Model");
            xml.writeAttribute(XMI, "id", "model_" + diagram.id());
            xml.writeAttribute("name", diagram.name());

            for (String scalar : SUPPORTED_SCALARS) {
                xml.writeStartElement("packagedElement");
                xml.writeAttribute(XMI, "type", "uml:PrimitiveType");
                xml.writeAttribute(XMI, "id", "primitive_" + scalar);
                xml.writeAttribute("name", scalar);
                xml.writeEndElement();
            }

            Set<UUID> packagedMembers = new HashSet<>();
            diagram.packages().forEach(value -> packagedMembers.addAll(value.memberIds()));
            for (DiagramDocument.PackageElement value : diagram.packages()) {
                if (value.parentId() == null) writePackage(xml, value, diagram);
            }
            for (DiagramDocument.ClassElement value : diagram.classes()) {
                if (!packagedMembers.contains(value.id())) writeClass(xml, value, diagram);
            }
            for (DiagramDocument.Enumeration value : diagram.enumerations()) {
                if (!packagedMembers.contains(value.id())) writeEnumeration(xml, value);
            }
            for (DiagramDocument.Association value : diagram.associations()) {
                if (!packagedMembers.contains(value.id())) writeAssociation(xml, value);
            }
            xml.writeEndElement();
            xml.writeEndElement();
            xml.writeEndDocument();
            xml.close();
            return output.toString();
        } catch (Exception exception) {
            throw new IllegalStateException("No se pudo exportar XMI", exception);
        }
    }

    private void writePackage(XMLStreamWriter xml, DiagramDocument.PackageElement value,
                              DiagramDocument diagram) throws Exception {
        xml.writeStartElement("packagedElement");
        xml.writeAttribute(XMI, "type", "uml:Package");
        xml.writeAttribute(XMI, "id", "package_" + value.id());
        xml.writeAttribute("name", value.name());
        for (DiagramDocument.PackageElement child : diagram.packages()) {
            if (value.id().equals(child.parentId())) writePackage(xml, child, diagram);
        }
        for (UUID memberId : value.memberIds()) {
            DiagramDocument.ClassElement classValue = diagram.classes().stream()
                .filter(item -> item.id().equals(memberId)).findFirst().orElse(null);
            DiagramDocument.Enumeration enumeration = diagram.enumerations().stream()
                .filter(item -> item.id().equals(memberId)).findFirst().orElse(null);
            DiagramDocument.Association association = diagram.associations().stream()
                .filter(item -> item.id().equals(memberId)).findFirst().orElse(null);
            if (classValue != null) writeClass(xml, classValue, diagram);
            else if (enumeration != null) writeEnumeration(xml, enumeration);
            else if (association != null) writeAssociation(xml, association);
        }
        xml.writeEndElement();
    }

    private void writeClass(XMLStreamWriter xml, DiagramDocument.ClassElement value,
                            DiagramDocument diagram) throws Exception {
        xml.writeStartElement("packagedElement");
        xml.writeAttribute(XMI, "type", "uml:Class");
        xml.writeAttribute(XMI, "id", "class_" + value.id());
        xml.writeAttribute("name", value.name());
        for (DiagramDocument.Attribute attribute : value.attributes()) {
            xml.writeStartElement("ownedAttribute");
            xml.writeAttribute(XMI, "type", "uml:Property");
            xml.writeAttribute(XMI, "id", "attribute_" + attribute.id());
            xml.writeAttribute("name", attribute.name());
            String enumerationId = diagram.enumerations().stream()
                .filter(item -> item.name().equals(attribute.type()) || item.id().toString().equals(attribute.type()))
                .map(item -> "enumeration_" + item.id()).findFirst().orElse(null);
            if (enumerationId != null) {
                xml.writeAttribute("type", enumerationId);
            } else {
                xml.writeAttribute("type", "primitive_" + attribute.type());
            }
            // EA renders UML multiplicity and isUnique=false as "[0..1] {bag}". Keep its
            // class compartment clean while retaining Collab Modeler's data constraints
            // in namespaced metadata for lossless re-import into this application.
            xml.writeAttribute("collab", COLLAB, "primaryKey", Boolean.toString(attribute.primaryKey()));
            xml.writeAttribute("collab", COLLAB, "required", Boolean.toString(attribute.required()));
            xml.writeAttribute("collab", COLLAB, "unique", Boolean.toString(attribute.unique()));
            xml.writeEndElement();
        }
        for (DiagramDocument.Generalization generalization : diagram.generalizations()) {
            if (!generalization.childId().equals(value.id())) continue;
            xml.writeStartElement("generalization");
            xml.writeAttribute(XMI, "type", "uml:Generalization");
            xml.writeAttribute(XMI, "id", "generalization_" + generalization.id());
            xml.writeAttribute("general", "class_" + generalization.parentId());
            xml.writeEndElement();
        }
        xml.writeEndElement();
    }

    private void writeEnumeration(XMLStreamWriter xml, DiagramDocument.Enumeration value) throws Exception {
        xml.writeStartElement("packagedElement");
        xml.writeAttribute(XMI, "type", "uml:Enumeration");
        xml.writeAttribute(XMI, "id", "enumeration_" + value.id());
        xml.writeAttribute("name", value.name());
        for (DiagramDocument.EnumerationValue literal : value.values()) {
            xml.writeStartElement("ownedLiteral");
            xml.writeAttribute(XMI, "type", "uml:EnumerationLiteral");
            xml.writeAttribute(XMI, "id", "literal_" + literal.id());
            xml.writeAttribute("name", literal.name());
            xml.writeEndElement();
        }
        xml.writeEndElement();
    }

    private void writeAssociation(XMLStreamWriter xml, DiagramDocument.Association value) throws Exception {
        String sourceEnd = "end_" + value.id() + "_source";
        String targetEnd = "end_" + value.id() + "_target";
        xml.writeStartElement("packagedElement");
        xml.writeAttribute(XMI, "type", "uml:Association");
        xml.writeAttribute(XMI, "id", "association_" + value.id());
        if (value.name() != null && !value.name().isBlank()) xml.writeAttribute("name", value.name());
        xml.writeAttribute("memberEnd", sourceEnd + " " + targetEnd);
        xml.writeAttribute("navigableOwnedEnd", "TARGET".equals(value.owningSide()) ? targetEnd : sourceEnd);
        writeAssociationEnd(xml, sourceEnd, value.sourceId(), value.sourceCardinality(), value.sourceRole());
        writeAssociationEnd(xml, targetEnd, value.targetId(), value.targetCardinality(), value.targetRole());
        xml.writeEndElement();
    }

    private void writeAssociationEnd(XMLStreamWriter xml, String id, UUID type, String cardinality,
                                     String role) throws Exception {
        xml.writeStartElement("ownedEnd");
        xml.writeAttribute(XMI, "type", "uml:Property");
        xml.writeAttribute(XMI, "id", id);
        xml.writeAttribute("type", "class_" + type);
        if (role != null && !role.isBlank()) xml.writeAttribute("name", role);
        String[] limits = cardinality.split("\\.\\.");
        writeMultiplicity(xml, limits[0], limits.length == 1 ? limits[0] : limits[1]);
        xml.writeEndElement();
    }

    private void writeMultiplicity(XMLStreamWriter xml, String lower, String upper) throws Exception {
        xml.writeStartElement("lowerValue");
        xml.writeAttribute(XMI, "type", "uml:LiteralInteger");
        xml.writeAttribute(XMI, "id", "value_" + UUID.randomUUID());
        xml.writeAttribute("value", lower);
        xml.writeEndElement();
        xml.writeStartElement("upperValue");
        xml.writeAttribute(XMI, "type", "uml:LiteralUnlimitedNatural");
        xml.writeAttribute(XMI, "id", "value_" + UUID.randomUUID());
        xml.writeAttribute("value", upper);
        xml.writeEndElement();
    }

    public ImportResult importXmi(byte[] bytes, String fallbackName) {
        if (bytes == null || bytes.length == 0) throw new IllegalArgumentException("El archivo XMI estÃ¡ vacÃ­o");
        if (bytes.length > MAX_BYTES) throw new IllegalArgumentException("El XMI supera el lÃ­mite de 5 MB");
        try {
            Document document = secureFactory().newDocumentBuilder().parse(new ByteArrayInputStream(bytes));
            Element root = document.getDocumentElement();
            if (root == null) throw new IllegalArgumentException("El XML no contiene un elemento raíz");
            if (document.getElementsByTagName("*").getLength() > MAX_ELEMENTS) {
                throw new IllegalArgumentException("El XMI contiene demasiados elementos");
            }
            assertDepth(root, 1);
            if (isEnterpriseArchitectNativeExport(root)) return importEnterpriseArchitectNative(root, fallbackName);
            if (!"XMI".equals(localName(root))) throw new IllegalArgumentException(
                "El archivo no es XMI 2.1 ni una exportación XML de Enterprise Architect compatible");
            if (!"2.1".equals(xmiAttribute(root, "version"))) throw new IllegalArgumentException("Solo se admite XMI 2.1");

            List<ImportWarning> warnings = new ArrayList<>();
            List<Element> packaged = elements(document, "packagedElement");
            Map<String, UUID> ids = new LinkedHashMap<>();
            Map<String, String> typeNames = new HashMap<>();
            Map<String, Element> packageElements = new LinkedHashMap<>();
            Set<String> usedTypeNames = new HashSet<>();

            for (Element element : packaged) {
                String type = xmiType(element);
                String externalId = xmiAttribute(element, "id");
                if (externalId.isBlank()) {
                    warnings.add(warning("MISSING_ID", "Se omitiÃ³ un elemento sin xmi:id", null, type));
                    continue;
                }
                if (Set.of("uml:Package", "uml:Class", "uml:Enumeration", "uml:Association").contains(type)) {
                    ids.put(externalId, stableId(externalId));
                    if ("uml:Package".equals(type)) packageElements.put(externalId, element);
                }
                if (Set.of("uml:PrimitiveType", "uml:DataType", "uml:Enumeration", "uml:Class").contains(type)) {
                    typeNames.put(externalId, element.getAttribute("name"));
                }
            }

            List<DiagramDocument.Enumeration> enumerations = new ArrayList<>();
            int position = 0;
            for (Element element : packaged) {
                if (!"uml:Enumeration".equals(xmiType(element))) continue;
                String externalId = xmiAttribute(element, "id");
                if (!ids.containsKey(externalId)) continue;
                String name = uniqueName(identifier(element.getAttribute("name"), "Enumeracion", warnings, externalId, "ENUMERATION"),
                    usedTypeNames, warnings, externalId, "ENUMERATION");
                typeNames.put(externalId, name);
                List<DiagramDocument.EnumerationValue> values = new ArrayList<>();
                Set<String> usedValues = new HashSet<>();
                for (Element literal : directChildren(element, "ownedLiteral")) {
                    String literalExternalId = xmiAttribute(literal, "id");
                    String literalName = uniqueName(identifier(literal.getAttribute("name"), "VALOR", warnings,
                        literalExternalId, "ENUMERATION_LITERAL"), usedValues, warnings, literalExternalId, "ENUMERATION_LITERAL");
                    values.add(new DiagramDocument.EnumerationValue(stableId(externalId + ":" + literalExternalId), literalName, 1));
                }
                enumerations.add(new DiagramDocument.Enumeration(ids.get(externalId), name, values, position(position++), 1));
            }

            List<DiagramDocument.ClassElement> classes = new ArrayList<>();
            for (Element element : packaged) {
                if (!"uml:Class".equals(xmiType(element))) continue;
                String externalId = xmiAttribute(element, "id");
                if (!ids.containsKey(externalId)) continue;
                String name = uniqueName(identifier(element.getAttribute("name"), "Clase", warnings, externalId, "CLASS"),
                    usedTypeNames, warnings, externalId, "CLASS");
                classes.add(new DiagramDocument.ClassElement(ids.get(externalId), name,
                    readAttributes(element, typeNames, warnings), position(position++), 1));
            }

            List<DiagramDocument.Association> associations = readAssociations(document, packaged, ids, warnings);
            List<DiagramDocument.Generalization> generalizations = readGeneralizations(packaged, ids, warnings);
            List<DiagramDocument.PackageElement> packages = readPackages(packageElements, ids, warnings);

            for (Element element : packaged) {
                String type = xmiType(element);
                if (!Set.of("uml:Package", "uml:Class", "uml:Enumeration", "uml:Association",
                    "uml:PrimitiveType", "uml:DataType").contains(type)) {
                    warnings.add(warning("UNSUPPORTED_ELEMENT", "Elemento XMI no soportado: " + type,
                        xmiAttribute(element, "id"), type));
                }
            }
            if (!elements(document, "Extension").isEmpty()) {
                warnings.add(warning("EA_EXTENSION_IGNORED",
                    "Se ignoraron extensiones propietarias de Enterprise Architect; el modelo semÃ¡ntico se conserva",
                    null, "xmi:Extension"));
            }
            DiagramDocument result = new DiagramDocument(UUID.randomUUID(), modelName(document, fallbackName), 0,
                classes, enumerations, associations, generalizations, packages);
            return new ImportResult(result, List.copyOf(warnings));
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("El archivo no es un XMI 2.1 vÃ¡lido o seguro", exception);
        }
    }

    private boolean isEnterpriseArchitectNativeExport(Element root) {
        return "Package".equals(localName(root)) && directChildren(root, "Table").stream()
            .anyMatch(table -> "t_object".equals(table.getAttribute("name")));
    }

    /** Imports the table-oriented XML produced by Enterprise Architect's native package export. */
    private ImportResult importEnterpriseArchitectNative(Element root, String fallbackName) {
        List<ImportWarning> warnings = new ArrayList<>();
        Map<String, Element> objects = new LinkedHashMap<>();
        Map<String, UUID> ids = new LinkedHashMap<>();
        for (Element row : eaRows(root, "t_object")) {
            String objectId = eaColumn(row, "Object_ID");
            String objectType = eaColumn(row, "Object_Type");
            if (!objectId.isBlank() && Set.of("Class", "Enumeration").contains(objectType)) {
                objects.put(objectId, row);
                ids.put(objectId, stableId("ea-object:" + eaExternalId(row, objectId)));
            }
        }

        Map<String, List<Element>> attributesByOwner = new HashMap<>();
        for (Element row : eaRows(root, "t_attribute")) {
            attributesByOwner.computeIfAbsent(eaColumn(row, "Object_ID"), ignored -> new ArrayList<>()).add(row);
        }
        Map<String, DiagramDocument.Position> positions = new HashMap<>();
        for (Element row : eaRows(root, "t_diagramobjects")) {
            positions.put(eaColumn(row, "Object_ID"), new DiagramDocument.Position(
                number(eaColumn(row, "RectLeft"), 0), -number(eaColumn(row, "RectTop"), 0)));
        }

        Set<String> usedNames = new HashSet<>();
        List<DiagramDocument.ClassElement> classes = new ArrayList<>();
        List<DiagramDocument.Enumeration> enumerations = new ArrayList<>();
        int index = 0;
        for (Map.Entry<String, Element> entry : objects.entrySet()) {
            String objectId = entry.getKey();
            Element row = entry.getValue();
            String externalId = eaExternalId(row, objectId);
            String objectType = eaColumn(row, "Object_Type");
            String name = uniqueName(identifier(eaColumn(row, "Name"),
                "Enumeration".equals(objectType) ? "Enumeracion" : "Clase", warnings, externalId,
                "Enumeration".equals(objectType) ? "ENUMERATION" : "CLASS"), usedNames, warnings, externalId,
                "Enumeration".equals(objectType) ? "ENUMERATION" : "CLASS");
            DiagramDocument.Position objectPosition = positions.getOrDefault(objectId, position(index++));
            if ("Enumeration".equals(objectType)) {
                List<DiagramDocument.EnumerationValue> values = new ArrayList<>();
                for (Element attribute : attributesByOwner.getOrDefault(objectId, List.of())) {
                    String attributeId = eaExternalId(attribute, eaColumn(attribute, "ID"));
                    values.add(new DiagramDocument.EnumerationValue(stableId("ea-literal:" + attributeId),
                        identifier(eaColumn(attribute, "Name"), "VALOR", warnings, attributeId, "ENUMERATION_LITERAL"), 1));
                }
                enumerations.add(new DiagramDocument.Enumeration(ids.get(objectId), name, values, objectPosition, 1));
                continue;
            }
            List<DiagramDocument.Attribute> attributes = new ArrayList<>();
            Set<String> attributeNames = new HashSet<>();
            for (Element attribute : attributesByOwner.getOrDefault(objectId, List.of())) {
                String attributeId = eaExternalId(attribute, eaColumn(attribute, "ID"));
                String attributeName = uniqueName(identifier(eaColumn(attribute, "Name"), "atributo", warnings,
                    attributeId, "PROPERTY"), attributeNames, warnings, attributeId, "PROPERTY");
                String rawType = eaColumn(attribute, "Type");
                String type = normalizeType(rawType);
                if (type == null) {
                    type = "String";
                    warnings.add(warning("UNSUPPORTED_PROPERTY_TYPE", "El tipo '" + rawType + "' se importó como String",
                        attributeId, "PROPERTY"));
                }
                attributes.add(new DiagramDocument.Attribute(stableId("ea-attribute:" + attributeId), attributeName, type,
                    "1".equals(eaColumn(attribute, "IsID")), !"0".equals(eaColumn(attribute, "LowerBound")), false, 1));
            }
            classes.add(new DiagramDocument.ClassElement(ids.get(objectId), name, attributes, objectPosition, 1));
        }

        List<DiagramDocument.Association> associations = new ArrayList<>();
        List<DiagramDocument.Generalization> generalizations = new ArrayList<>();
        for (Element row : eaRows(root, "t_connector")) {
            String connectorId = eaExternalId(row, eaColumn(row, "Connector_ID"));
            UUID source = ids.get(eaColumn(row, "Start_Object_ID"));
            UUID target = ids.get(eaColumn(row, "End_Object_ID"));
            if (source == null || target == null) {
                warnings.add(warning("EXTERNAL_ASSOCIATION_END", "Se omitió un conector que referencia elementos externos",
                    connectorId, "CONNECTOR"));
                continue;
            }
            String connectorType = eaColumn(row, "Connector_Type");
            if ("Generalization".equalsIgnoreCase(connectorType)) {
                generalizations.add(new DiagramDocument.Generalization(stableId("ea-generalization:" + connectorId), target, source, 1));
            } else if ("Association".equalsIgnoreCase(connectorType) || "Aggregation".equalsIgnoreCase(connectorType)) {
                associations.add(new DiagramDocument.Association(stableId("ea-association:" + connectorId), source, target,
                    eaCardinality(eaColumn(row, "SourceCard"), warnings, connectorId),
                    eaCardinality(eaColumn(row, "DestCard"), warnings, connectorId),
                    nullableIdentifier(eaColumn(row, "Name"), warnings, connectorId, "ASSOCIATION"),
                    nullableIdentifier(eaColumn(row, "SourceRole"), warnings, connectorId, "ROLE"),
                    nullableIdentifier(eaColumn(row, "DestRole"), warnings, connectorId, "ROLE"), "SOURCE", 1));
            } else {
                warnings.add(warning("UNSUPPORTED_ELEMENT", "Conector de Enterprise Architect no soportado: " + connectorType,
                    connectorId, "CONNECTOR"));
            }
        }

        String packageName = root.getAttribute("name").isBlank() ? fallbackName : root.getAttribute("name");
        String packageExternalId = root.getAttribute("guid").isBlank() ? packageName : root.getAttribute("guid");
        List<UUID> members = new ArrayList<>(ids.values());
        associations.forEach(value -> members.add(value.id()));
        DiagramDocument.PackageElement packageElement = new DiagramDocument.PackageElement(
            stableId("ea-package:" + packageExternalId),
            identifier(packageName, "Paquete", warnings, packageExternalId, "PACKAGE"), null, members, 1);
        warnings.add(warning("EA_NATIVE_XML_IMPORTED",
            "Se importó una exportación XML nativa de Enterprise Architect; no se conservan sus metadatos ni estilo visual propietarios",
            packageExternalId, "EA_PACKAGE"));
        return new ImportResult(new DiagramDocument(UUID.randomUUID(), packageName, 0,
            classes, enumerations, associations, generalizations, List.of(packageElement)), List.copyOf(warnings));
    }

    private List<Element> eaRows(Element root, String tableName) {
        for (Element table : directChildren(root, "Table")) {
            if (tableName.equals(table.getAttribute("name"))) return directChildren(table, "Row");
        }
        return List.of();
    }

    private String eaColumn(Element row, String name) {
        for (Element column : directChildren(row, "Column")) {
            if (name.equals(column.getAttribute("name"))) return column.getAttribute("value");
        }
        return "";
    }

    private String eaExternalId(Element row, String fallback) {
        String guid = eaColumn(row, "ea_guid");
        return guid.isBlank() ? fallback : guid;
    }

    private double number(String value, double fallback) {
        try { return Double.parseDouble(value); }
        catch (NumberFormatException ignored) { return fallback; }
    }

    private String eaCardinality(String value, List<ImportWarning> warnings, String externalId) {
        String normalized = value == null ? "" : value.trim().replace("n", "*").replace("N", "*");
        if (normalized.isBlank()) return "1";
        if (Set.of("1", "0..1", "0..*", "1..*").contains(normalized)) return normalized;
        warnings.add(warning("NORMALIZED_CARDINALITY", "La cardinalidad '" + value + "' se aproximó al contrato soportado",
            externalId, "ASSOCIATION_END"));
        if (normalized.contains("*")) return normalized.startsWith("1") ? "1..*" : "0..*";
        return normalized.startsWith("0") ? "0..1" : "1";
    }

    private List<DiagramDocument.Attribute> readAttributes(Element owner, Map<String, String> typeNames,
                                                            List<ImportWarning> warnings) {
        List<DiagramDocument.Attribute> result = new ArrayList<>();
        Set<String> names = new HashSet<>();
        for (Element attribute : directChildren(owner, "ownedAttribute")) {
            if (!attribute.getAttribute("association").isBlank()) continue;
            String externalId = xmiAttribute(attribute, "id");
            String name = uniqueName(identifier(attribute.getAttribute("name"), "atributo", warnings, externalId, "PROPERTY"),
                names, warnings, externalId, "PROPERTY");
            String typeReference = attribute.getAttribute("type");
            String rawType = typeNames.getOrDefault(typeReference, typeReference);
            if (rawType.isBlank()) {
                List<Element> typeNodes = directChildren(attribute, "type");
                if (!typeNodes.isEmpty()) {
                    String href = typeNodes.getFirst().getAttribute("href");
                    rawType = href.contains("#") ? href.substring(href.lastIndexOf('#') + 1) : typeNodes.getFirst().getAttribute("name");
                }
            }
            String normalized = normalizeType(rawType);
            if (normalized == null && !typeReference.isBlank() && typeNames.containsKey(typeReference)) {
                normalized = typeNames.get(typeReference);
            }
            if (normalized == null) {
                normalized = "String";
                warnings.add(warning("UNSUPPORTED_PROPERTY_TYPE",
                    "El tipo '" + rawType + "' se importÃ³ como String", externalId, "PROPERTY"));
            }
            String lower = bound(attribute, "lowerValue", "1");
            boolean primaryKey = booleanMetadata(attribute, "primaryKey", "isID", false);
            boolean required = booleanMetadata(attribute, "required", null, !"0".equals(lower));
            boolean unique = booleanMetadata(attribute, "unique", "isUnique", false);
            result.add(new DiagramDocument.Attribute(stableId("attribute:" + externalId), name, normalized,
                primaryKey, required, unique, 1));
        }
        return result;
    }

    private boolean booleanMetadata(Element element, String collabName, String standardName, boolean fallback) {
        String collabValue = element.getAttributeNS(COLLAB, collabName);
        if (!collabValue.isBlank()) return Boolean.parseBoolean(collabValue);
        if (standardName != null && element.hasAttribute(standardName)) {
            return Boolean.parseBoolean(element.getAttribute(standardName));
        }
        return fallback;
    }

    private List<DiagramDocument.Association> readAssociations(Document document, List<Element> packaged,
                                                                Map<String, UUID> ids,
                                                                List<ImportWarning> warnings) {
        List<DiagramDocument.Association> result = new ArrayList<>();
        List<Element> allProperties = elements(document, "ownedAttribute");
        for (Element association : packaged) {
            if (!"uml:Association".equals(xmiType(association))) continue;
            String externalId = xmiAttribute(association, "id");
            List<Element> ends = new ArrayList<>(directChildren(association, "ownedEnd"));
            for (Element property : allProperties) {
                if (externalId.equals(property.getAttribute("association"))) ends.add(property);
            }
            LinkedHashMap<String, Element> distinct = new LinkedHashMap<>();
            ends.forEach(end -> distinct.put(xmiAttribute(end, "id"), end));
            ends = new ArrayList<>(distinct.values());
            if (ends.size() != 2) {
                warnings.add(warning("UNSUPPORTED_ASSOCIATION_ENDS",
                    "Se omitiÃ³ una asociaciÃ³n que no tiene exactamente dos extremos", externalId, "ASSOCIATION"));
                continue;
            }
            Element sourceEnd = ends.get(0);
            Element targetEnd = ends.get(1);
            UUID source = ids.get(sourceEnd.getAttribute("type"));
            UUID target = ids.get(targetEnd.getAttribute("type"));
            if (source == null || target == null) {
                warnings.add(warning("EXTERNAL_ASSOCIATION_END",
                    "Se omitiÃ³ una asociaciÃ³n que referencia clases externas", externalId, "ASSOCIATION"));
                continue;
            }
            String targetEndId = xmiAttribute(targetEnd, "id");
            String owningSide = containsToken(association.getAttribute("navigableOwnedEnd"), targetEndId) ? "TARGET" : "SOURCE";
            result.add(new DiagramDocument.Association(ids.get(externalId), source, target,
                cardinality(sourceEnd, warnings), cardinality(targetEnd, warnings),
                nullableIdentifier(association.getAttribute("name"), warnings, externalId, "ASSOCIATION"),
                nullableIdentifier(sourceEnd.getAttribute("name"), warnings, xmiAttribute(sourceEnd, "id"), "ROLE"),
                nullableIdentifier(targetEnd.getAttribute("name"), warnings, targetEndId, "ROLE"), owningSide, 1));
        }
        return result;
    }

    private List<DiagramDocument.Generalization> readGeneralizations(List<Element> packaged, Map<String, UUID> ids,
                                                                     List<ImportWarning> warnings) {
        List<DiagramDocument.Generalization> result = new ArrayList<>();
        for (Element childElement : packaged) {
            if (!"uml:Class".equals(xmiType(childElement))) continue;
            UUID child = ids.get(xmiAttribute(childElement, "id"));
            for (Element value : directChildren(childElement, "generalization")) {
                String externalId = xmiAttribute(value, "id");
                UUID parent = ids.get(value.getAttribute("general"));
                if (child == null || parent == null) {
                    warnings.add(warning("EXTERNAL_GENERALIZATION",
                        "Se omitiÃ³ una generalizaciÃ³n con una clase externa", externalId, "GENERALIZATION"));
                } else {
                    result.add(new DiagramDocument.Generalization(stableId("generalization:" + externalId), parent, child, 1));
                }
            }
        }
        return result;
    }

    private List<DiagramDocument.PackageElement> readPackages(Map<String, Element> packageElements,
                                                               Map<String, UUID> ids,
                                                               List<ImportWarning> warnings) {
        List<DiagramDocument.PackageElement> result = new ArrayList<>();
        for (Map.Entry<String, Element> entry : packageElements.entrySet()) {
            String externalId = entry.getKey();
            Element element = entry.getValue();
            Element parent = nearestPackage(element.getParentNode());
            List<UUID> members = new ArrayList<>();
            for (Element child : directChildren(element, "packagedElement")) {
                if (Set.of("uml:Class", "uml:Enumeration", "uml:Association").contains(xmiType(child))) {
                    UUID memberId = ids.get(xmiAttribute(child, "id"));
                    if (memberId != null) members.add(memberId);
                }
            }
            result.add(new DiagramDocument.PackageElement(ids.get(externalId),
                identifier(element.getAttribute("name"), "Paquete", warnings, externalId, "PACKAGE"),
                parent == null ? null : ids.get(xmiAttribute(parent, "id")), members, 1));
        }
        return result;
    }

    private DocumentBuilderFactory secureFactory() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        return factory;
    }

    private void assertDepth(Node node, int depth) {
        if (depth > MAX_DEPTH) throw new IllegalArgumentException("El XMI excede la profundidad mÃ¡xima permitida");
        NodeList children = node.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i) instanceof Element) assertDepth(children.item(i), depth + 1);
        }
    }

    private List<Element> elements(Document document, String wantedName) {
        NodeList nodes = document.getElementsByTagNameNS("*", wantedName);
        List<Element> result = new ArrayList<>();
        for (int i = 0; i < nodes.getLength(); i++) result.add((Element) nodes.item(i));
        if (!result.isEmpty()) return result;
        nodes = document.getElementsByTagName(wantedName);
        for (int i = 0; i < nodes.getLength(); i++) result.add((Element) nodes.item(i));
        return result;
    }

    private List<Element> directChildren(Element parent, String wantedName) {
        List<Element> result = new ArrayList<>();
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i) instanceof Element element && wantedName.equals(localName(element))) result.add(element);
        }
        return result;
    }

    private Element nearestPackage(Node node) {
        Node current = node;
        while (current instanceof Element element) {
            if ("packagedElement".equals(localName(element)) && "uml:Package".equals(xmiType(element))) return element;
            current = current.getParentNode();
        }
        return null;
    }

    private String xmiType(Element element) { return xmiAttribute(element, "type"); }

    private String xmiAttribute(Element element, String wantedLocalName) {
        NamedNodeMap attributes = element.getAttributes();
        for (int i = 0; i < attributes.getLength(); i++) {
            Node attribute = attributes.item(i);
            String namespace = attribute.getNamespaceURI();
            if (wantedLocalName.equals(attribute.getLocalName())
                && ("xmi".equalsIgnoreCase(attribute.getPrefix())
                    || namespace != null && namespace.toUpperCase(Locale.ROOT).contains("XMI"))) {
                return attribute.getNodeValue();
            }
        }
        return element.getAttribute("xmi:" + wantedLocalName);
    }

    private String localName(Node node) {
        return node.getLocalName() == null ? node.getNodeName().replaceFirst("^.*:", "") : node.getLocalName();
    }

    private String cardinality(Element end, List<ImportWarning> warnings) {
        String lower = bound(end, "lowerValue", "1");
        String upper = bound(end, "upperValue", "1");
        String value = lower.equals(upper) ? lower : lower + ".." + upper;
        if (Set.of("0..1", "1", "0..*", "1..*").contains(value)) return value;
        warnings.add(warning("NORMALIZED_CARDINALITY", "La cardinalidad '" + value + "' se aproximÃ³ al contrato soportado",
            xmiAttribute(end, "id"), "ASSOCIATION_END"));
        if ("*".equals(upper) || "-1".equals(upper)) return "0".equals(lower) ? "0..*" : "1..*";
        return "0".equals(lower) ? "0..1" : "1";
    }

    private String bound(Element owner, String childName, String fallback) {
        List<Element> values = directChildren(owner, childName);
        if (values.isEmpty()) return fallback;
        String value = values.getFirst().getAttribute("value");
        if (value.isBlank()) value = values.getFirst().getTextContent().trim();
        return "-1".equals(value) ? "*" : value.isBlank() ? fallback : value;
    }

    private String normalizeType(String value) {
        if (value == null) return null;
        String raw = value.replace("primitive_", "").replace("EAnone_", "").trim();
        String result = switch (raw.toLowerCase(Locale.ROOT)) {
            case "string", "char", "character", "varchar" -> "String";
            case "text", "clob" -> "Text";
            case "integer", "int", "short" -> "Integer";
            case "long", "biginteger" -> "Long";
            case "boolean", "bool" -> "Boolean";
            case "date" -> "Date";
            case "datetime", "timestamp" -> "DateTime";
            case "uuid", "guid" -> "UUID";
            case "decimal", "bigdecimal", "double", "float", "real" -> "Decimal";
            case "binary", "blob", "byte[]" -> "Binary";
            default -> null;
        };
        return result != null && SUPPORTED_SCALARS.contains(result) ? result : null;
    }

    private DiagramDocument.Position position(int index) {
        return new DiagramDocument.Position(100 + (index % 4) * 280, 100 + (index / 4) * 240);
    }

    private UUID stableId(String externalId) {
        return UUID.nameUUIDFromBytes(("collab-modeler:xmi:" + externalId).getBytes(StandardCharsets.UTF_8));
    }

    private String identifier(String raw, String fallback, List<ImportWarning> warnings,
                              String externalId, String elementType) {
        String value = raw == null || raw.isBlank() ? fallback : raw.trim();
        if (VALID_NAME.matcher(value).matches() && value.length() <= 120) return value;
        String normalized = value.replaceAll("[^A-Za-z0-9_]", "_");
        if (normalized.isBlank() || (!Character.isLetter(normalized.charAt(0)) && normalized.charAt(0) != '_')) normalized = "_" + normalized;
        if (normalized.length() > 120) normalized = normalized.substring(0, 120);
        warnings.add(warning("NORMALIZED_NAME", "El nombre '" + value + "' se normalizÃ³ como '" + normalized + "'",
            externalId, elementType));
        return normalized;
    }

    private String nullableIdentifier(String raw, List<ImportWarning> warnings, String externalId, String elementType) {
        return raw == null || raw.isBlank() ? null : identifier(raw, "elemento", warnings, externalId, elementType);
    }

    private String uniqueName(String value, Set<String> used, List<ImportWarning> warnings,
                              String externalId, String elementType) {
        String candidate = value;
        int suffix = 2;
        while (!used.add(candidate.toLowerCase(Locale.ROOT))) candidate = value + "_" + suffix++;
        if (!candidate.equals(value)) warnings.add(warning("DUPLICATE_NAME",
            "El nombre duplicado '" + value + "' se importÃ³ como '" + candidate + "'", externalId, elementType));
        return candidate;
    }

    private String modelName(Document document, String fallback) {
        for (Element model : elements(document, "Model")) {
            String name = model.getAttribute("name");
            if (!name.isBlank()) return name;
        }
        return fallback;
    }

    private boolean containsToken(String tokens, String value) {
        if (value == null || value.isBlank()) return false;
        for (String token : tokens.trim().split("\\s+")) if (token.equals(value)) return true;
        return false;
    }

    private ImportWarning warning(String code, String message, String externalId, String elementType) {
        return new ImportWarning(code, message, externalId, elementType);
    }

    public record ImportWarning(String code, String message, String externalId, String elementType) {}
    public record ImportResult(DiagramDocument diagram, List<ImportWarning> warnings) {}
}
