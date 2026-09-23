package com.collabmodeler.api.ai;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashSet;
import java.util.Set;

final class ImageProposalValidator {
    private static final Set<String> TYPES = Set.of("String", "Text", "Integer", "Long", "Decimal", "Boolean", "Date", "DateTime", "UUID", "Binary");
    private static final Set<String> CARDINALITIES = Set.of("0..1", "1", "0..*", "1..*");

    static JsonNode validate(JsonNode proposal) {
        if (proposal == null || !proposal.isObject() || !proposal.path("classes").isArray()
            || !proposal.path("associations").isArray() || !proposal.path("warnings").isArray()
            || !proposal.path("confidence").isNumber()) throw invalid();
        double confidence = proposal.path("confidence").asDouble();
        if (!Double.isFinite(confidence) || confidence < 0 || confidence > 1) throw invalid();
        Set<String> names = new HashSet<>();
        if (proposal.path("classes").size() > 50 || proposal.path("associations").size() > 100 || proposal.path("warnings").size() > 50) throw invalid();
        for (JsonNode item : proposal.path("classes")) {
            String name = name(item.path("name"));
            if (!names.add(name.toLowerCase(java.util.Locale.ROOT)) || !item.path("attributes").isArray() || item.path("attributes").size() > 100) throw invalid();
            Set<String> attributes = new HashSet<>();
            for (JsonNode attribute : item.path("attributes")) {
                if (!attributes.add(name(attribute.path("name")).toLowerCase(java.util.Locale.ROOT))
                    || !TYPES.contains(attribute.path("type").asText())
                    || !attribute.path("primaryKey").isBoolean() || !attribute.path("required").isBoolean()
                    || !attribute.path("unique").isBoolean()) throw invalid();
            }
        }
        for (JsonNode link : proposal.path("associations")) {
            if (!names.contains(link.path("source").asText().toLowerCase(java.util.Locale.ROOT))
                || !names.contains(link.path("target").asText().toLowerCase(java.util.Locale.ROOT))
                || !CARDINALITIES.contains(link.path("sourceCardinality").asText())
                || !CARDINALITIES.contains(link.path("targetCardinality").asText())
                || (link.hasNonNull("name") && !link.path("name").isTextual())
                || (link.hasNonNull("sourceRole") && !link.path("sourceRole").isTextual())
                || (link.hasNonNull("targetRole") && !link.path("targetRole").isTextual())
                || !Set.of("SOURCE", "TARGET").contains(link.path("owningSide").asText())) throw invalid();
        }
        for (JsonNode warning : proposal.path("warnings")) if (!warning.isTextual() || warning.asText().length() > 500) throw invalid();
        return proposal;
    }

    private static String name(JsonNode value) {
        if (!value.isTextual() || !value.asText().matches("[A-Za-z_][A-Za-z0-9_]{0,79}")) throw invalid();
        return value.asText();
    }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("El adaptador de visión devolvió una propuesta inválida"); }
}
