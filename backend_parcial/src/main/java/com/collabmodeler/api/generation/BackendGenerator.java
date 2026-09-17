package com.collabmodeler.api.generation;

import com.collabmodeler.api.diagram.DiagramDocument;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class BackendGenerator {
    private final ObjectMapper mapper;
    public BackendGenerator(ObjectMapper mapper) { this.mapper = mapper; }

    public byte[] generate(DiagramDocument diagram, String groupId, String artifactId) {
        validate(diagram, groupId, artifactId);
        String basePackage = groupId + "." + artifactId.replace('-', '_');
        String javaRoot = "src/main/java/" + basePackage.replace('.', '/') + "/";
        Map<String, String> files = new LinkedHashMap<>();
        Map<String, Object> trace = new LinkedHashMap<>();
        files.put("pom.xml", pom(groupId, artifactId));
        files.put("src/main/resources/application.yml", applicationYml());
        files.put("src/main/resources/db/migration/V1__initial_schema.sql", migration(diagram));
        files.put("Dockerfile", dockerfile());
        files.put("docker-compose.yml", compose(artifactId));
        files.put(javaRoot + "Application.java", application(basePackage));
        files.put(javaRoot + "model/ModelElement.java", modelElementAnnotation(basePackage));

        for (var item : diagram.classes()) {
            String className = javaName(item.name(), true);
            String entityPath = javaRoot + "model/" + className + ".java";
            files.put(entityPath, entity(basePackage, item));
            files.put(javaRoot + "repository/" + className + "Repository.java", repository(basePackage, className, idType(item)));
            files.put(javaRoot + "dto/" + className + "Dto.java", dto(basePackage, item));
            files.put(javaRoot + "service/" + className + "Service.java", service(basePackage, item));
            files.put(javaRoot + "controller/" + className + "Controller.java", controller(basePackage, item));
            trace.put(item.id().toString(), Map.of("kind", "class", "file", entityPath, "line", lineOf(files.get(entityPath), "public class " + className)));
            for (var attribute : item.attributes()) {
                trace.put(attribute.id().toString(), Map.of("kind", "attribute", "file", entityPath, "line", lineOf(files.get(entityPath), "private " + javaType(attribute.type()) + " " + javaName(attribute.name(), false))));
            }
        }
        try { files.put("model-traceability.json", mapper.writerWithDefaultPrettyPrinter().writeValueAsString(trace)); }
        catch (Exception exception) { throw new IllegalStateException(exception); }
        return zip(files);
    }

    private void validate(DiagramDocument diagram, String groupId, String artifactId) {
        if (!groupId.matches("[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)+")) throw new IllegalArgumentException("groupId inválido");
        if (!artifactId.matches("[a-z][a-z0-9-]*")) throw new IllegalArgumentException("artifactId inválido");
        Set<String> names = new HashSet<>();
        for (var item : diagram.classes()) {
            if (!item.name().matches("[A-Za-z][A-Za-z0-9_]*")) throw new IllegalArgumentException("Nombre de clase inválido: " + item.name());
            if (!names.add(item.name().toLowerCase(Locale.ROOT))) throw new IllegalArgumentException("Nombre de clase duplicado: " + item.name());
            long primaryKeys = item.attributes().stream().filter(DiagramDocument.Attribute::primaryKey).count();
            if (primaryKeys != 1) throw new IllegalArgumentException("La clase " + item.name() + " debe tener exactamente una clave primaria");
        }
    }

    private String pom(String groupId, String artifactId) { return """
        <?xml version="1.0" encoding="UTF-8"?>
        <project xmlns="http://maven.apache.org/POM/4.0.0"><modelVersion>4.0.0</modelVersion>
          <parent><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-parent</artifactId><version>3.5.16</version><relativePath/></parent>
          <groupId>%s</groupId><artifactId>%s</artifactId><version>0.0.1-SNAPSHOT</version>
          <properties><java.version>21</java.version></properties>
          <dependencies>
            <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-web</artifactId></dependency>
            <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-data-jpa</artifactId></dependency>
            <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-validation</artifactId></dependency>
            <dependency><groupId>org.flywaydb</groupId><artifactId>flyway-core</artifactId></dependency>
            <dependency><groupId>org.flywaydb</groupId><artifactId>flyway-database-postgresql</artifactId></dependency>
            <dependency><groupId>org.postgresql</groupId><artifactId>postgresql</artifactId><scope>runtime</scope></dependency>
            <dependency><groupId>org.springdoc</groupId><artifactId>springdoc-openapi-starter-webmvc-ui</artifactId><version>2.8.14</version></dependency>
            <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-test</artifactId><scope>test</scope></dependency>
          </dependencies>
          <build><plugins><plugin><groupId>org.springframework.boot</groupId><artifactId>spring-boot-maven-plugin</artifactId></plugin></plugins></build>
        </project>
        """.formatted(groupId, artifactId); }
    private String application(String pkg) { return "package " + pkg + ";\n\nimport org.springframework.boot.SpringApplication;\nimport org.springframework.boot.autoconfigure.SpringBootApplication;\n\n@SpringBootApplication\npublic class Application { public static void main(String[] args) { SpringApplication.run(Application.class, args); } }\n"; }
    private String modelElementAnnotation(String pkg) { return "package " + pkg + ".model;\n\nimport java.lang.annotation.*;\n\n@Retention(RetentionPolicy.SOURCE)\n@Target({ElementType.TYPE, ElementType.FIELD})\npublic @interface ModelElement { String value(); }\n"; }

    private String entity(String pkg, DiagramDocument.ClassElement item) {
        String name = javaName(item.name(), true);
        StringBuilder body = new StringBuilder("package ").append(pkg).append(".model;\n\nimport jakarta.persistence.*;\nimport java.math.BigDecimal;\nimport java.time.*;\nimport java.util.UUID;\n\n@Entity\n@Table(name = \"").append(sqlName(item.name())).append("\")\n@ModelElement(\"").append(item.id()).append("\")\npublic class ").append(name).append(" {\n");
        for (var attribute : item.attributes()) {
            if (attribute.primaryKey()) body.append("    @Id\n");
            body.append("    @ModelElement(\"").append(attribute.id()).append("\")\n")
                .append("    @Column(name = \"").append(sqlName(attribute.name())).append("\", nullable = ").append(!attribute.required()).append(", unique = ").append(attribute.unique()).append(")\n")
                .append("    private ").append(javaType(attribute.type())).append(" ").append(javaName(attribute.name(), false)).append(";\n\n");
        }
        body.append("    public ").append(name).append("() {}\n\n");
        for (var attribute : item.attributes()) {
            String field = javaName(attribute.name(), false); String type = javaType(attribute.type()); String cap = javaName(attribute.name(), true);
            body.append("    public ").append(type).append(" get").append(cap).append("() { return ").append(field).append("; }\n")
                .append("    public void set").append(cap).append("(").append(type).append(" value) { this.").append(field).append(" = value; }\n");
        }
        return body.append("}\n").toString();
    }

    private String dto(String pkg, DiagramDocument.ClassElement item) {
        String fields = item.attributes().stream().map(a -> javaType(a.type()) + " " + javaName(a.name(), false)).reduce((a,b) -> a + ", " + b).orElse("");
        return "package " + pkg + ".dto;\n\nimport java.math.BigDecimal;\nimport java.time.*;\nimport java.util.UUID;\n\npublic record " + javaName(item.name(), true) + "Dto(" + fields + ") {}\n";
    }
    private String repository(String pkg, String name, String idType) { return "package " + pkg + ".repository;\n\nimport " + pkg + ".model." + name + ";\nimport org.springframework.data.jpa.repository.JpaRepository;\nimport java.math.BigDecimal;\nimport java.time.*;\nimport java.util.UUID;\n\npublic interface " + name + "Repository extends JpaRepository<" + name + ", " + idType + "> {}\n"; }
    private String service(String pkg, DiagramDocument.ClassElement item) {
        String name = javaName(item.name(), true); String id = idType(item); String idField = javaName(item.attributes().stream().filter(DiagramDocument.Attribute::primaryKey).findFirst().orElseThrow().name(), false);
        StringBuilder setters = new StringBuilder();
        for (var a : item.attributes()) setters.append("        entity.set").append(javaName(a.name(), true)).append("(dto.").append(javaName(a.name(), false)).append("());\n");
        return "package " + pkg + ".service;\n\nimport " + pkg + ".dto." + name + "Dto;\nimport " + pkg + ".model." + name + ";\nimport " + pkg + ".repository." + name + "Repository;\nimport org.springframework.stereotype.Service;\nimport java.math.BigDecimal;\nimport java.time.*;\nimport java.util.*;\n\n@Service\npublic class " + name + "Service {\n    private final " + name + "Repository repository;\n    public " + name + "Service(" + name + "Repository repository) { this.repository = repository; }\n    public List<" + name + "> list() { return repository.findAll(); }\n    public " + name + " get(" + id + " id) { return repository.findById(id).orElseThrow(); }\n    public " + name + " create(" + name + "Dto dto) { var entity = new " + name + "();\n" + setters + "        return repository.save(entity);\n    }\n    public void delete(" + id + " id) { repository.deleteById(id); }\n}\n";
    }
    private String controller(String pkg, DiagramDocument.ClassElement item) {
        String name = javaName(item.name(), true); String variable = javaName(item.name(), false); String id = idType(item);
        return "package " + pkg + ".controller;\n\nimport " + pkg + ".dto." + name + "Dto;\nimport " + pkg + ".model." + name + ";\nimport " + pkg + ".service." + name + "Service;\nimport jakarta.validation.Valid;\nimport org.springframework.http.HttpStatus;\nimport org.springframework.web.bind.annotation.*;\nimport java.math.BigDecimal;\nimport java.time.*;\nimport java.util.*;\n\n@RestController\n@RequestMapping(\"/api/" + sqlName(item.name()) + "\")\npublic class " + name + "Controller {\n    private final " + name + "Service service;\n    public " + name + "Controller(" + name + "Service service) { this.service = service; }\n    @GetMapping public List<" + name + "> list() { return service.list(); }\n    @GetMapping(\"/{id}\") public " + name + " get(@PathVariable " + id + " id) { return service.get(id); }\n    @PostMapping @ResponseStatus(HttpStatus.CREATED) public " + name + " create(@Valid @RequestBody " + name + "Dto dto) { return service.create(dto); }\n    @DeleteMapping(\"/{id}\") @ResponseStatus(HttpStatus.NO_CONTENT) public void delete(@PathVariable " + id + " id) { service.delete(id); }\n}\n";
    }

    private String migration(DiagramDocument diagram) {
        StringBuilder sql = new StringBuilder();
        for (var item : diagram.classes()) {
            sql.append("create table ").append(sqlName(item.name())).append(" (\n");
            for (int i = 0; i < item.attributes().size(); i++) {
                var a = item.attributes().get(i);
                sql.append("    ").append(sqlName(a.name())).append(" ").append(sqlType(a.type()));
                if (a.primaryKey()) sql.append(" primary key"); if (a.required()) sql.append(" not null"); if (a.unique()) sql.append(" unique");
                sql.append(i + 1 == item.attributes().size() ? "\n" : ",\n");
            }
            sql.append(");\n\n");
        }
        return sql.toString();
    }
    private String applicationYml() { return "spring:\n  datasource:\n    url: ${DATABASE_URL:jdbc:postgresql://localhost:5432/app}\n    username: ${DATABASE_USER:app}\n    password: ${DATABASE_PASSWORD:app}\n  jpa:\n    hibernate:\n      ddl-auto: validate\n    open-in-view: false\n"; }
    private String dockerfile() { return "FROM eclipse-temurin:21-jre\nWORKDIR /app\nCOPY target/*.jar app.jar\nEXPOSE 8080\nENTRYPOINT [\"java\",\"-jar\",\"app.jar\"]\n"; }
    private String compose(String artifact) { return "services:\n  db:\n    image: postgres:17-alpine\n    environment:\n      POSTGRES_DB: app\n      POSTGRES_USER: app\n      POSTGRES_PASSWORD: app\n    ports: [\"5432:5432\"]\n  api:\n    build: .\n    environment:\n      DATABASE_URL: jdbc:postgresql://db:5432/app\n    ports: [\"8080:8080\"]\n    depends_on: [db]\n"; }
    private String idType(DiagramDocument.ClassElement item) { return javaType(item.attributes().stream().filter(DiagramDocument.Attribute::primaryKey).findFirst().orElseThrow().type()); }
    private String javaType(String type) { return switch (type) { case "Integer" -> "Integer"; case "Long" -> "Long"; case "Decimal" -> "BigDecimal"; case "Boolean" -> "Boolean"; case "Date" -> "LocalDate"; case "DateTime" -> "LocalDateTime"; case "UUID" -> "UUID"; case "Binary" -> "byte[]"; default -> "String"; }; }
    private String sqlType(String type) { return switch (type) { case "Integer" -> "integer"; case "Long" -> "bigint"; case "Decimal" -> "numeric(19,2)"; case "Boolean" -> "boolean"; case "Date" -> "date"; case "DateTime" -> "timestamp with time zone"; case "UUID" -> "uuid"; case "Binary" -> "bytea"; case "Text" -> "text"; default -> "varchar(255)"; }; }
    private String javaName(String value, boolean capitalize) { String clean = value.replaceAll("[^A-Za-z0-9_]", "_"); return capitalize ? Character.toUpperCase(clean.charAt(0)) + clean.substring(1) : Character.toLowerCase(clean.charAt(0)) + clean.substring(1); }
    private String sqlName(String value) { return value.replaceAll("([a-z0-9])([A-Z])", "$1_$2").replaceAll("[^A-Za-z0-9]", "_").toLowerCase(Locale.ROOT); }
    private int lineOf(String content, String needle) { int index = content.indexOf(needle); return index < 0 ? 1 : content.substring(0, index).split("\\R", -1).length; }
    private byte[] zip(Map<String, String> files) {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            try (ZipOutputStream zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
                for (var file : files.entrySet()) { zip.putNextEntry(new ZipEntry(file.getKey())); zip.write(file.getValue().getBytes(StandardCharsets.UTF_8)); zip.closeEntry(); }
            }
            return output.toByteArray();
        } catch (Exception exception) { throw new IllegalStateException("No se pudo generar el ZIP", exception); }
    }
}
