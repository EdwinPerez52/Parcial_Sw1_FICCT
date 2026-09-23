package com.collabmodeler.api.generation;

import com.collabmodeler.api.diagram.DiagramDocument;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;

@Service
public class MobileSpecService {
    private final ObjectMapper mapper;
    private final String signingKey;

    public MobileSpecService(ObjectMapper mapper,
                             @Value("${app.mobile-spec.secret:modeler-mobile-spec-secret-signing-key-32b}") String signingKey) {
        this.mapper = mapper;
        this.signingKey = signingKey;
    }

    public String generateSpecJson(DiagramDocument diagram, UUID versionId, String openapiYaml) {
        try {
            String openapiHash = sha256Hex(openapiYaml != null ? openapiYaml : "");
            Map<String, Object> spec = new LinkedHashMap<>();
            spec.put("specVersion", "1.0.0");
            spec.put("diagramId", diagram.id().toString());
            spec.put("versionId", versionId != null ? versionId.toString() : diagram.id().toString());
            spec.put("revision", diagram.revision());
            spec.put("diagramName", diagram.name());
            spec.put("issuedAt", Instant.now().toString());
            spec.put("openapiFile", "openapi.yaml");
            spec.put("openapiHash", openapiHash);
            spec.put("openapiYaml", openapiYaml != null ? openapiYaml : "");

            Map<UUID, UUID> childToParent = new HashMap<>();
            Map<UUID, String> classNames = new HashMap<>();
            for (var item : diagram.classes()) {
                classNames.put(item.id(), item.name());
            }
            if (diagram.generalizations() != null) {
                for (var g : diagram.generalizations()) {
                    childToParent.put(g.childId(), g.parentId());
                }
            }

            List<Map<String, Object>> entities = new ArrayList<>();
            for (var item : diagram.classes()) {
                Map<String, Object> entity = new LinkedHashMap<>();
                entity.put("id", item.id().toString());
                entity.put("name", item.name());
                entity.put("tableName", sqlName(item.name()));
                if (childToParent.containsKey(item.id())) {
                    UUID pId = childToParent.get(item.id());
                    entity.put("parentId", pId.toString());
                    entity.put("parentName", classNames.get(pId));
                }
                List<Map<String, Object>> attributes = new ArrayList<>();
                for (var attr : item.attributes()) {
                    Map<String, Object> a = new LinkedHashMap<>();
                    a.put("id", attr.id().toString());
                    a.put("name", attr.name());
                    a.put("type", attr.type());
                    a.put("primaryKey", attr.primaryKey());
                    a.put("required", attr.required());
                    a.put("unique", attr.unique());
                    attributes.add(a);
                }
                entity.put("attributes", attributes);
                entities.add(entity);
            }
            spec.put("entities", entities);

            List<Map<String, Object>> enums = new ArrayList<>();
            for (var en : diagram.enumerations()) {
                Map<String, Object> e = new LinkedHashMap<>();
                e.put("id", en.id().toString());
                e.put("name", en.name());
                e.put("values", en.values().stream().map(DiagramDocument.EnumerationValue::name).toList());
                enums.add(e);
            }
            spec.put("enumerations", enums);

            List<Map<String, Object>> associations = new ArrayList<>();
            for (var a : diagram.associations()) {
                Map<String, Object> assoc = new LinkedHashMap<>();
                assoc.put("id", a.id().toString());
                assoc.put("sourceId", a.sourceId().toString());
                assoc.put("targetId", a.targetId().toString());
                assoc.put("sourceCardinality", a.sourceCardinality());
                assoc.put("targetCardinality", a.targetCardinality());
                assoc.put("name", a.name());
                assoc.put("owningSide", a.owningSide());
                associations.add(assoc);
            }
            spec.put("associations", associations);

            List<Map<String, Object>> generalizations = new ArrayList<>();
            if (diagram.generalizations() != null) {
                for (var g : diagram.generalizations()) {
                    Map<String, Object> gen = new LinkedHashMap<>();
                    gen.put("id", g.id().toString());
                    gen.put("parentId", g.parentId().toString());
                    gen.put("childId", g.childId().toString());
                    generalizations.add(gen);
                }
            }
            spec.put("generalizations", generalizations);

            // Canonical JSON string of payload to sign
            String payloadToSign = mapper.writeValueAsString(spec);
            String signature = hmacSha256Hex(payloadToSign, signingKey);
            spec.put("signature", signature);

            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(spec);
        } catch (Exception exception) {
            throw new IllegalStateException("No se pudo emitir modeler-mobile-spec.json", exception);
        }
    }

    /**
     * Generates a spec JSON with a unique nonce for single-use by the local agent.
     * The nonce is covered by the HMAC-SHA256 signature.
     */
    public String generateAgentSpecJson(DiagramDocument diagram, UUID versionId, String openapiYaml) {
        try {
            String openapiHash = sha256Hex(openapiYaml != null ? openapiYaml : "");
            Map<String, Object> spec = new LinkedHashMap<>();
            spec.put("specVersion", "1.0.0");
            spec.put("diagramId", diagram.id().toString());
            spec.put("versionId", versionId != null ? versionId.toString() : diagram.id().toString());
            spec.put("revision", diagram.revision());
            spec.put("diagramName", diagram.name());
            spec.put("issuedAt", Instant.now().toString());
            spec.put("nonce", UUID.randomUUID().toString());
            spec.put("openapiFile", "openapi.yaml");
            spec.put("openapiHash", openapiHash);
            spec.put("openapiYaml", openapiYaml != null ? openapiYaml : "");

            Map<UUID, UUID> childToParent = new HashMap<>();
            Map<UUID, String> classNames = new HashMap<>();
            for (var item : diagram.classes()) {
                classNames.put(item.id(), item.name());
            }
            if (diagram.generalizations() != null) {
                for (var g : diagram.generalizations()) {
                    childToParent.put(g.childId(), g.parentId());
                }
            }

            List<Map<String, Object>> entities = new ArrayList<>();
            for (var item : diagram.classes()) {
                Map<String, Object> entity = new LinkedHashMap<>();
                entity.put("id", item.id().toString());
                entity.put("name", item.name());
                entity.put("tableName", sqlName(item.name()));
                if (childToParent.containsKey(item.id())) {
                    UUID pId = childToParent.get(item.id());
                    entity.put("parentId", pId.toString());
                    entity.put("parentName", classNames.get(pId));
                }
                List<Map<String, Object>> attributes = new ArrayList<>();
                for (var attr : item.attributes()) {
                    Map<String, Object> a = new LinkedHashMap<>();
                    a.put("id", attr.id().toString());
                    a.put("name", attr.name());
                    a.put("type", attr.type());
                    a.put("primaryKey", attr.primaryKey());
                    a.put("required", attr.required());
                    a.put("unique", attr.unique());
                    attributes.add(a);
                }
                entity.put("attributes", attributes);
                entities.add(entity);
            }
            spec.put("entities", entities);

            List<Map<String, Object>> enums = new ArrayList<>();
            for (var en : diagram.enumerations()) {
                Map<String, Object> e = new LinkedHashMap<>();
                e.put("id", en.id().toString());
                e.put("name", en.name());
                e.put("values", en.values().stream().map(DiagramDocument.EnumerationValue::name).toList());
                enums.add(e);
            }
            spec.put("enumerations", enums);

            List<Map<String, Object>> associations = new ArrayList<>();
            for (var a : diagram.associations()) {
                Map<String, Object> assoc = new LinkedHashMap<>();
                assoc.put("id", a.id().toString());
                assoc.put("sourceId", a.sourceId().toString());
                assoc.put("targetId", a.targetId().toString());
                assoc.put("sourceCardinality", a.sourceCardinality());
                assoc.put("targetCardinality", a.targetCardinality());
                assoc.put("name", a.name());
                assoc.put("owningSide", a.owningSide());
                associations.add(assoc);
            }
            spec.put("associations", associations);

            List<Map<String, Object>> generalizations = new ArrayList<>();
            if (diagram.generalizations() != null) {
                for (var g : diagram.generalizations()) {
                    Map<String, Object> gen = new LinkedHashMap<>();
                    gen.put("id", g.id().toString());
                    gen.put("parentId", g.parentId().toString());
                    gen.put("childId", g.childId().toString());
                    generalizations.add(gen);
                }
            }
            spec.put("generalizations", generalizations);

            String payloadToSign = mapper.writeValueAsString(spec);
            String signature = hmacSha256Hex(payloadToSign, signingKey);
            spec.put("signature", signature);

            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(spec);
        } catch (Exception exception) {
            throw new IllegalStateException("No se pudo emitir modeler-mobile-spec.json con nonce", exception);
        }
    }

    public boolean verifySignature(Map<String, Object> spec) {
        try {
            if (!spec.containsKey("signature")) return false;
            String signature = String.valueOf(spec.get("signature"));
            Map<String, Object> copy = new LinkedHashMap<>(spec);
            copy.remove("signature");
            String payload = mapper.writeValueAsString(copy);
            String expected = hmacSha256Hex(payload, signingKey);
            return MessageDigest.isEqual(signature.getBytes(StandardCharsets.UTF_8), expected.getBytes(StandardCharsets.UTF_8));
        } catch (Exception exception) {
            return false;
        }
    }

    public String signSpec(Map<String, Object> unsignedSpec) {
        try {
            Map<String, Object> value = new LinkedHashMap<>(unsignedSpec); value.remove("signature");
            value.put("signature", hmacSha256Hex(mapper.writeValueAsString(value), signingKey));
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(value);
        } catch (Exception exception) { throw new IllegalStateException("No se pudo firmar la especificación", exception); }
    }

    private static String sha256Hex(String input) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(hash);
    }

    private static String hmacSha256Hex(String data, String key) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] result = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(result);
    }

    private static String sqlName(String value) {
        return value.replaceAll("([a-z0-9])([A-Z])", "$1_$2").replaceAll("[^A-Za-z0-9]", "_").toLowerCase(Locale.ROOT);
    }
}
