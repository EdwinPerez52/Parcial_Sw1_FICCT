package com.collabmodeler.api.exchange;

import com.collabmodeler.api.diagram.DiagramDocument;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamWriter;
import java.io.ByteArrayInputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
public class XmiService {
    private static final String XMI = "http://www.omg.org/spec/XMI/20131001";
    private static final String UML = "http://www.omg.org/spec/UML/20131001";

    public String exportXmi(DiagramDocument diagram) {
        try {
            StringWriter output = new StringWriter();
            XMLStreamWriter xml = XMLOutputFactory.newFactory().createXMLStreamWriter(output);
            xml.writeStartDocument("UTF-8", "1.0");
            xml.writeStartElement("xmi", "XMI", XMI);
            xml.writeNamespace("xmi", XMI);
            xml.writeNamespace("uml", UML);
            xml.writeAttribute(XMI, "version", "2.1");
            xml.writeStartElement("uml", "Model", UML);
            xml.writeAttribute(XMI, "id", "model_" + diagram.id());
            xml.writeAttribute("name", diagram.name());

            for (var item : diagram.classes()) {
                xml.writeStartElement("packagedElement");
                xml.writeAttribute(XMI, "type", "uml:Class");
                xml.writeAttribute(XMI, "id", "class_" + item.id());
                xml.writeAttribute("name", item.name());
                for (var attribute : item.attributes()) {
                    xml.writeStartElement("ownedAttribute");
                    xml.writeAttribute(XMI, "id", "attribute_" + attribute.id());
                    xml.writeAttribute("name", attribute.name());
                    xml.writeAttribute("type", "primitive_" + attribute.type());
                    if (attribute.primaryKey()) xml.writeAttribute("isID", "true");
                    xml.writeEndElement();
                }
                xml.writeEndElement();
            }

            for (var link : diagram.associations()) {
                xml.writeStartElement("packagedElement");
                xml.writeAttribute(XMI, "type", "uml:Association");
                xml.writeAttribute(XMI, "id", "association_" + link.id());
                if (link.name() != null) xml.writeAttribute("name", link.name());
                writeEnd(xml, link.id() + "_source", link.sourceId(), link.sourceCardinality());
                writeEnd(xml, link.id() + "_target", link.targetId(), link.targetCardinality());
                xml.writeEndElement();
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

    private void writeEnd(XMLStreamWriter xml, String id, UUID type, String cardinality) throws Exception {
        xml.writeStartElement("ownedEnd");
        xml.writeAttribute(XMI, "id", "end_" + id);
        xml.writeAttribute("type", "class_" + type);
        String[] limits = cardinality.split("\\.\\.");
        xml.writeStartElement("lowerValue");
        xml.writeAttribute(XMI, "type", "uml:LiteralInteger");
        xml.writeAttribute("value", limits[0]);
        xml.writeEndElement();
        xml.writeStartElement("upperValue");
        xml.writeAttribute(XMI, "type", "uml:LiteralUnlimitedNatural");
        xml.writeAttribute("value", limits.length == 1 ? limits[0] : limits[1]);
        xml.writeEndElement();
        xml.writeEndElement();
    }

    public ImportResult importXmi(byte[] bytes, String fallbackName) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            Document document = factory.newDocumentBuilder().parse(new ByteArrayInputStream(bytes));

            Map<String, UUID> ids = new LinkedHashMap<>();
            List<DiagramDocument.ClassElement> classes = new ArrayList<>();
            List<String> warnings = new ArrayList<>();
            NodeList elements = document.getElementsByTagName("packagedElement");
            int classPosition = 0;
            for (int i = 0; i < elements.getLength(); i++) {
                Element element = (Element) elements.item(i);
                if (!"uml:Class".equals(xmiAttribute(element, "type"))) continue;
                String externalId = xmiAttribute(element, "id");
                UUID id = UUID.randomUUID();
                ids.put(externalId, id);
                List<DiagramDocument.Attribute> attributes = new ArrayList<>();
                NodeList owned = element.getElementsByTagName("ownedAttribute");
                for (int j = 0; j < owned.getLength(); j++) {
                    Element attribute = (Element) owned.item(j);
                    String name = attribute.getAttribute("name");
                    if (name.isBlank()) { warnings.add("Se omitió un atributo sin nombre en " + element.getAttribute("name")); continue; }
                    attributes.add(new DiagramDocument.Attribute(UUID.randomUUID(), name,
                        normalizeType(attribute.getAttribute("type")), "true".equals(attribute.getAttribute("isID")), false, false));
                }
                classes.add(new DiagramDocument.ClassElement(id, element.getAttribute("name"), attributes,
                    new DiagramDocument.Position(100 + (classPosition % 4) * 280, 100 + (classPosition / 4) * 240), 1));
                classPosition++;
            }

            List<DiagramDocument.Association> associations = new ArrayList<>();
            for (int i = 0; i < elements.getLength(); i++) {
                Element element = (Element) elements.item(i);
                if (!"uml:Association".equals(xmiAttribute(element, "type"))) continue;
                NodeList ends = element.getElementsByTagName("ownedEnd");
                if (ends.getLength() != 2) { warnings.add("Se omitió una asociación que no tiene dos extremos"); continue; }
                Element first = (Element) ends.item(0); Element second = (Element) ends.item(1);
                UUID source = ids.get(first.getAttribute("type")); UUID target = ids.get(second.getAttribute("type"));
                if (source == null || target == null) { warnings.add("Se omitió una asociación con clases externas"); continue; }
                associations.add(new DiagramDocument.Association(UUID.randomUUID(), source, target,
                    cardinality(first), cardinality(second), element.getAttribute("name"), 1));
            }
            DiagramDocument result = new DiagramDocument(UUID.randomUUID(), modelName(document, fallbackName), 0, classes, associations);
            return new ImportResult(result, warnings);
        } catch (Exception exception) {
            throw new IllegalArgumentException("El archivo no es un XMI válido o seguro", exception);
        }
    }

    private String xmiAttribute(Element element, String localName) {
        String value = element.getAttributeNS(XMI, localName);
        return value.isBlank() ? element.getAttribute("xmi:" + localName) : value;
    }
    private String normalizeType(String value) {
        String raw = value.replace("primitive_", "");
        return switch (raw.toLowerCase(Locale.ROOT)) {
            case "integer", "int" -> "Integer"; case "long" -> "Long"; case "boolean" -> "Boolean";
            case "date" -> "Date"; case "datetime" -> "DateTime"; case "uuid" -> "UUID";
            case "decimal", "bigdecimal" -> "Decimal"; default -> "String";
        };
    }
    private String cardinality(Element end) {
        NodeList lowers = end.getElementsByTagName("lowerValue"); NodeList uppers = end.getElementsByTagName("upperValue");
        String lower = lowers.getLength() == 0 ? "0" : ((Element) lowers.item(0)).getAttribute("value");
        String upper = uppers.getLength() == 0 ? "1" : ((Element) uppers.item(0)).getAttribute("value");
        return lower.equals(upper) ? lower : lower + ".." + upper;
    }
    private String modelName(Document document, String fallback) {
        NodeList models = document.getElementsByTagNameNS(UML, "Model");
        if (models.getLength() == 0) return fallback;
        String name = ((Element) models.item(0)).getAttribute("name");
        return name.isBlank() ? fallback : name;
    }
    public record ImportResult(DiagramDocument diagram, List<String> warnings) {}
}

