package com.collabmodeler.api.generation;

import com.collabmodeler.api.diagram.DiagramDocument;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class OpenApiGenerator {

    public String generateYaml(DiagramDocument diagram, String artifactId) {
        StringBuilder sb = new StringBuilder();
        sb.append("openapi: 3.0.3\n");
        sb.append("info:\n");
        sb.append("  title: \"").append(diagram.name()).append(" API\"\n");
        sb.append("  description: \"Backend Spring Boot generado desde Collab Modeler (revisión ").append(diagram.revision()).append(")\"\n");
        sb.append("  version: \"1.0.0\"\n");
        sb.append("servers:\n");
        sb.append("  - url: http://localhost:8080\n");
        sb.append("    description: Servidor local\n");
        sb.append("security:\n");
        sb.append("  - BearerAuth: []\n");
        sb.append("paths:\n");

        // Auth paths
        sb.append("""
          /api/auth/register:
            post:
              tags: [Autenticación]
              summary: Registro de usuario (primer usuario obtiene ADMIN)
              security: []
              requestBody:
                required: true
                content:
                  application/json:
                    schema:
                      $ref: '#/components/schemas/AuthRegisterRequest'
              responses:
                '200':
                  description: Usuario registrado exitosamente
                  content:
                    application/json:
                      schema:
                        $ref: '#/components/schemas/AuthTokenResponse'
                '400':
                  description: Datos inválidos
                  content:
                    application/json:
                      schema:
                        $ref: '#/components/schemas/ErrorResponse'
          /api/auth/login:
            post:
              tags: [Autenticación]
              summary: Inicio de sesión con correo y contraseña
              security: []
              requestBody:
                required: true
                content:
                  application/json:
                    schema:
                      $ref: '#/components/schemas/AuthLoginRequest'
              responses:
                '200':
                  description: Credenciales válidas
                  content:
                    application/json:
                      schema:
                        $ref: '#/components/schemas/AuthTokenResponse'
                '401':
                  description: Credenciales inválidas
                  content:
                    application/json:
                      schema:
                        $ref: '#/components/schemas/ErrorResponse'
          /api/auth/refresh:
            post:
              tags: [Autenticación]
              summary: Refrescar token de acceso
              security: []
              requestBody:
                required: true
                content:
                  application/json:
                    schema:
                      $ref: '#/components/schemas/AuthRefreshRequest'
              responses:
                '200':
                  description: Token refrescado
                  content:
                    application/json:
                      schema:
                        $ref: '#/components/schemas/AuthTokenResponse'
                '401':
                  description: Token de refresco inválido o expirado
                  content:
                    application/json:
                      schema:
                        $ref: '#/components/schemas/ErrorResponse'
          /api/auth/me:
            get:
              tags: [Autenticación]
              summary: Obtener perfil del usuario autenticado
              responses:
                '200':
                  description: Perfil actual
                  content:
                    application/json:
                      schema:
                        $ref: '#/components/schemas/AuthUserResponse'
                '401':
                  description: No autenticado
                  content:
                    application/json:
                      schema:
                        $ref: '#/components/schemas/ErrorResponse'
        """);

        // Entity CRUD paths
        for (var item : diagram.classes()) {
            String name = javaName(item.name(), true);
            String resource = sqlName(item.name());
            String idParamType = openApiType(idType(diagram, item));

            sb.append("  /api/").append(resource).append(":\n");
            sb.append("    get:\n");
            sb.append("      tags: [\"").append(name).append("\"]\n");
            sb.append("      summary: \"Listar ").append(name).append("\"\n");
            sb.append("      responses:\n");
            sb.append("        '200':\n");
            sb.append("          description: \"Lista de ").append(name).append("\"\n");
            sb.append("          content:\n");
            sb.append("            application/json:\n");
            sb.append("              schema:\n");
            sb.append("                type: array\n");
            sb.append("                items:\n");
            sb.append("                  $ref: '#/components/schemas/").append(name).append("OutputDto'\n");
            sb.append("    post:\n");
            sb.append("      tags: [\"").append(name).append("\"]\n");
            sb.append("      summary: \"Crear ").append(name).append("\"\n");
            sb.append("      requestBody:\n");
            sb.append("        required: true\n");
            sb.append("        content:\n");
            sb.append("          application/json:\n");
            sb.append("            schema:\n");
            sb.append("              $ref: '#/components/schemas/").append(name).append("InputDto'\n");
            sb.append("      responses:\n");
            sb.append("        '201':\n");
            sb.append("          description: \"").append(name).append(" creado\"\n");
            sb.append("          content:\n");
            sb.append("            application/json:\n");
            sb.append("              schema:\n");
            sb.append("                $ref: '#/components/schemas/").append(name).append("OutputDto'\n");
            sb.append("        '400':\n");
            sb.append("          description: Datos inválidos\n");
            sb.append("          content:\n");
            sb.append("            application/json:\n");
            sb.append("              schema:\n");
            sb.append("                $ref: '#/components/schemas/ErrorResponse'\n");

            sb.append("  /api/").append(resource).append("/{id}:\n");
            sb.append("    get:\n");
            sb.append("      tags: [\"").append(name).append("\"]\n");
            sb.append("      summary: \"Obtener ").append(name).append(" por ID\"\n");
            sb.append("      parameters:\n");
            sb.append("        - name: id\n");
            sb.append("          in: path\n");
            sb.append("          required: true\n");
            sb.append("          schema:\n");
            sb.append("            ").append(idParamType).append("\n");
            sb.append("      responses:\n");
            sb.append("        '200':\n");
            sb.append("          description: \"").append(name).append(" encontrado\"\n");
            sb.append("          content:\n");
            sb.append("            application/json:\n");
            sb.append("              schema:\n");
            sb.append("                $ref: '#/components/schemas/").append(name).append("OutputDto'\n");
            sb.append("        '404':\n");
            sb.append("          description: \"No encontrado\"\n");
            sb.append("          content:\n");
            sb.append("            application/json:\n");
            sb.append("              schema:\n");
            sb.append("                $ref: '#/components/schemas/ErrorResponse'\n");

            sb.append("    put:\n");
            sb.append("      tags: [\"").append(name).append("\"]\n");
            sb.append("      summary: \"Actualizar ").append(name).append("\"\n");
            sb.append("      parameters:\n");
            sb.append("        - name: id\n");
            sb.append("          in: path\n");
            sb.append("          required: true\n");
            sb.append("          schema:\n");
            sb.append("            ").append(idParamType).append("\n");
            sb.append("      requestBody:\n");
            sb.append("        required: true\n");
            sb.append("        content:\n");
            sb.append("          application/json:\n");
            sb.append("            schema:\n");
            sb.append("              $ref: '#/components/schemas/").append(name).append("InputDto'\n");
            sb.append("      responses:\n");
            sb.append("        '200':\n");
            sb.append("          description: \"").append(name).append(" actualizado\"\n");
            sb.append("          content:\n");
            sb.append("            application/json:\n");
            sb.append("              schema:\n");
            sb.append("                $ref: '#/components/schemas/").append(name).append("OutputDto'\n");
            sb.append("        '400':\n");
            sb.append("          description: Datos inválidos\n");
            sb.append("        '404':\n");
            sb.append("          description: No encontrado\n");

            sb.append("    delete:\n");
            sb.append("      tags: [\"").append(name).append("\"]\n");
            sb.append("      summary: \"Eliminar ").append(name).append("\"\n");
            sb.append("      parameters:\n");
            sb.append("        - name: id\n");
            sb.append("          in: path\n");
            sb.append("          required: true\n");
            sb.append("          schema:\n");
            sb.append("            ").append(idParamType).append("\n");
            sb.append("      responses:\n");
            sb.append("        '204':\n");
            sb.append("          description: \"Eliminado exitosamente\"\n");
            sb.append("        '404':\n");
            sb.append("          description: No encontrado\n");
        }

        // Components & Schemas
        sb.append("components:\n");
        sb.append("  securitySchemes:\n");
        sb.append("    BearerAuth:\n");
        sb.append("      type: http\n");
        sb.append("      scheme: bearer\n");
        sb.append("      bearerFormat: JWT\n");
        sb.append("  schemas:\n");

        sb.append("""
            AuthRegisterRequest:
              type: object
              required: [email, password, fullName]
              properties:
                email:
                  type: string
                  format: email
                password:
                  type: string
                  format: password
                  minLength: 6
                fullName:
                  type: string
            AuthLoginRequest:
              type: object
              required: [email, password]
              properties:
                email:
                  type: string
                  format: email
                password:
                  type: string
                  format: password
            AuthRefreshRequest:
              type: object
              required: [refreshToken]
              properties:
                refreshToken:
                  type: string
            AuthUserResponse:
              type: object
              properties:
                id:
                  type: string
                  format: uuid
                email:
                  type: string
                fullName:
                  type: string
                role:
                  type: string
                  enum: [ROLE_ADMIN, ROLE_USER]
            AuthTokenResponse:
              type: object
              properties:
                accessToken:
                  type: string
                refreshToken:
                  type: string
                tokenType:
                  type: string
                  example: Bearer
                user:
                  $ref: '#/components/schemas/AuthUserResponse'
            ErrorResponse:
              type: object
              properties:
                status:
                  type: integer
                error:
                  type: string
                message:
                  type: string
                path:
                  type: string
                timestamp:
                  type: string
                  format: date-time
                validationErrors:
                  type: object
                  additionalProperties:
                    type: string
        """);

        // Entity DTO Schemas
        for (var item : diagram.classes()) {
            String name = javaName(item.name(), true);

            // Input DTO
            sb.append("    ").append(name).append("InputDto:\n");
            sb.append("      type: object\n");
            List<String> requiredFields = new ArrayList<>();
            for (var attr : item.attributes()) {
                if (attr.required() && !attr.primaryKey()) {
                    requiredFields.add(javaName(attr.name(), false));
                }
            }
            if (!requiredFields.isEmpty()) {
                sb.append("      required: ").append(requiredFields).append("\n");
            }
            sb.append("      properties:\n");
            for (var attr : item.attributes()) {
                if (attr.primaryKey()) continue;
                sb.append("        ").append(javaName(attr.name(), false)).append(":\n");
                sb.append("          ").append(openApiType(attr.type())).append("\n");
            }
            // Relation fields for input (e.g. clienteId for ManyToOne)
            for (var assoc : diagram.associations()) {
                if (assoc.sourceId().equals(item.id())) {
                    var targetClass = findClass(diagram, assoc.targetId());
                    if (isManyToOne(assoc.sourceCardinality(), assoc.targetCardinality())) {
                        sb.append("        ").append(javaName(targetClass.name(), false)).append("Id:\n");
                        sb.append("          type: string\n          format: uuid\n");
                    } else if (isManyToMany(assoc.sourceCardinality(), assoc.targetCardinality())) {
                        sb.append("        ").append(javaName(targetClass.name(), false)).append("Ids:\n");
                        sb.append("          type: array\n          items:\n            type: string\n            format: uuid\n");
                    }
                } else if (assoc.targetId().equals(item.id())) {
                    var sourceClass = findClass(diagram, assoc.sourceId());
                    if (isOneToMany(assoc.sourceCardinality(), assoc.targetCardinality())) {
                        sb.append("        ").append(javaName(sourceClass.name(), false)).append("Id:\n");
                        sb.append("          type: string\n          format: uuid\n");
                    }
                }
            }

            // Output DTO
            sb.append("    ").append(name).append("OutputDto:\n");
            sb.append("      type: object\n");
            sb.append("      properties:\n");
            for (var attr : item.attributes()) {
                sb.append("        ").append(javaName(attr.name(), false)).append(":\n");
                sb.append("          ").append(openApiType(attr.type())).append("\n");
            }
            // Output relations
            for (var assoc : diagram.associations()) {
                if (assoc.sourceId().equals(item.id())) {
                    var targetClass = findClass(diagram, assoc.targetId());
                    if (isManyToOne(assoc.sourceCardinality(), assoc.targetCardinality())) {
                        sb.append("        ").append(javaName(targetClass.name(), false)).append("Id:\n");
                        sb.append("          type: string\n          format: uuid\n");
                    } else if (isManyToMany(assoc.sourceCardinality(), assoc.targetCardinality())) {
                        sb.append("        ").append(javaName(targetClass.name(), false)).append("Ids:\n");
                        sb.append("          type: array\n          items:\n            type: string\n            format: uuid\n");
                    }
                } else if (assoc.targetId().equals(item.id())) {
                    var sourceClass = findClass(diagram, assoc.sourceId());
                    if (isOneToMany(assoc.sourceCardinality(), assoc.targetCardinality())) {
                        sb.append("        ").append(javaName(sourceClass.name(), false)).append("Id:\n");
                        sb.append("          type: string\n          format: uuid\n");
                    }
                }
            }
        }

        return sb.toString();
    }

    private static String openApiType(String type) {
        return switch (type) {
            case "Integer" -> "type: integer\n          format: int32";
            case "Long" -> "type: integer\n          format: int64";
            case "Decimal" -> "type: number\n          format: double";
            case "Boolean" -> "type: boolean";
            case "Date" -> "type: string\n          format: date";
            case "DateTime" -> "type: string\n          format: date-time";
            case "UUID" -> "type: string\n          format: uuid";
            case "Binary" -> "type: string\n          format: binary";
            case "Text" -> "type: string";
            default -> "type: string";
        };
    }

    private static String idType(DiagramDocument diagram, DiagramDocument.ClassElement item) {
        var pk = item.attributes().stream().filter(DiagramDocument.Attribute::primaryKey).findFirst();
        if (pk.isPresent()) return pk.get().type();
        for (var gen : diagram.generalizations()) {
            if (gen.childId().equals(item.id())) {
                var parent = findClass(diagram, gen.parentId());
                return idType(diagram, parent);
            }
        }
        return "UUID";
    }

    private static DiagramDocument.ClassElement findClass(DiagramDocument diagram, UUID id) {
        return diagram.classes().stream().filter(c -> c.id().equals(id)).findFirst().orElseThrow();
    }

    private static boolean isManyToOne(String sourceCard, String targetCard) {
        return (sourceCard.contains("*") || sourceCard.contains("N")) && (targetCard.equals("1") || targetCard.equals("0..1"));
    }

    private static boolean isOneToMany(String sourceCard, String targetCard) {
        return (sourceCard.equals("1") || sourceCard.equals("0..1")) && (targetCard.contains("*") || targetCard.contains("N"));
    }

    private static boolean isManyToMany(String sourceCard, String targetCard) {
        return (sourceCard.contains("*") || sourceCard.contains("N")) && (targetCard.contains("*") || targetCard.contains("N"));
    }

    private static String javaName(String value, boolean capitalize) {
        String clean = value.replaceAll("[^A-Za-z0-9_]", "_");
        return capitalize ? Character.toUpperCase(clean.charAt(0)) + clean.substring(1) : Character.toLowerCase(clean.charAt(0)) + clean.substring(1);
    }

    private static String sqlName(String value) {
        return value.replaceAll("([a-z0-9])([A-Z])", "$1_$2").replaceAll("[^A-Za-z0-9]", "_").toLowerCase(Locale.ROOT);
    }
}
