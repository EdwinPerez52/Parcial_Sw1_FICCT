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
    private final ModelValidator validator;
    private final OpenApiGenerator openApiGenerator;

    @org.springframework.beans.factory.annotation.Autowired
    public BackendGenerator(ObjectMapper mapper, ModelValidator validator, OpenApiGenerator openApiGenerator) {
        this.mapper = mapper;
        this.validator = validator;
        this.openApiGenerator = openApiGenerator;
    }

    public BackendGenerator(ObjectMapper mapper) {
        this(mapper, new ModelValidator(), new OpenApiGenerator());
    }

    public byte[] generate(DiagramDocument diagram, String groupId, String artifactId) {
        validator.validate(diagram, groupId, artifactId);

        String basePackage = groupId + "." + artifactId.replace('-', '_');
        String javaRoot = "src/main/java/" + basePackage.replace('.', '/') + "/";
        String testRoot = "src/test/java/" + basePackage.replace('.', '/') + "/";

        Map<String, String> files = new LinkedHashMap<>();
        Map<String, Object> trace = new LinkedHashMap<>();

        // 1. Build and configuration files
        files.put("pom.xml", pom(groupId, artifactId));
        files.put("src/main/resources/application.yml", applicationYml());
        files.put("Dockerfile", dockerfile());
        files.put("docker-compose.yml", compose(artifactId));
        files.put("README.md", readme(diagram, artifactId));

        // 2. OpenAPI specification
        String openApiYaml = openApiGenerator.generateYaml(diagram, artifactId);
        files.put("openapi.yaml", openApiYaml);
        files.put("src/main/resources/openapi.yaml", openApiYaml);

        // 3. Database Migration
        files.put("src/main/resources/db/migration/V1__initial_schema.sql", migration(diagram));

        // 4. Base Application & Annotation
        files.put(javaRoot + "Application.java", application(basePackage));
        files.put(javaRoot + "model/ModelElement.java", modelElementAnnotation(basePackage));

        // 5. Error handling
        files.put(javaRoot + "error/ResourceNotFoundException.java", resourceNotFoundException(basePackage));
        files.put(javaRoot + "error/ErrorResponse.java", errorResponse(basePackage));
        files.put(javaRoot + "error/GlobalExceptionHandler.java", globalExceptionHandler(basePackage));

        // 6. Security and Authentication (Isolated in .auth package and tables)
        files.put(javaRoot + "auth/AuthRole.java", authRole(basePackage));
        files.put(javaRoot + "auth/AuthUser.java", authUser(basePackage));
        files.put(javaRoot + "auth/AuthRefreshToken.java", authRefreshToken(basePackage));
        files.put(javaRoot + "auth/AuthUserRepository.java", authUserRepository(basePackage));
        files.put(javaRoot + "auth/AuthTokenRepository.java", authTokenRepository(basePackage));
        files.put(javaRoot + "auth/AuthDtos.java", authDtos(basePackage));
        files.put(javaRoot + "auth/JwtService.java", jwtService(basePackage));
        files.put(javaRoot + "auth/AuthService.java", authService(basePackage));
        files.put(javaRoot + "auth/JwtAuthenticationFilter.java", jwtAuthenticationFilter(basePackage));
        files.put(javaRoot + "auth/SecurityConfig.java", securityConfig(basePackage));
        files.put(javaRoot + "auth/AuthController.java", authController(basePackage));

        // 7. Enumerations
        for (var en : diagram.enumerations()) {
            String enumName = javaName(en.name(), true);
            String enumPath = javaRoot + "model/" + enumName + ".java";
            files.put(enumPath, enumeration(basePackage, en));
            trace.put(en.id().toString(), Map.of(
                "kind", "enumeration",
                "name", enumName,
                "file", enumPath,
                "line", lineOf(files.get(enumPath), "@ModelElement(\"" + en.id() + "\")")
            ));
        }

        // 8. Entities, DTOs, Mappers, Repositories, Services, Controllers
        for (var item : diagram.classes()) {
            String className = javaName(item.name(), true);
            String entityPath = javaRoot + "model/" + className + ".java";
            files.put(entityPath, entity(basePackage, diagram, item));
            if (isExplicitJoinClass(diagram, item)) {
                files.put(javaRoot + "model/" + className + "Id.java", compositeId(basePackage, diagram, item));
            }
            files.put(javaRoot + "dto/" + className + "InputDto.java", inputDto(basePackage, diagram, item));
            files.put(javaRoot + "dto/" + className + "OutputDto.java", outputDto(basePackage, diagram, item));
            files.put(javaRoot + "mapper/" + className + "Mapper.java", mapper(basePackage, diagram, item));
            files.put(javaRoot + "repository/" + className + "Repository.java", repository(basePackage, diagram, item));
            files.put(javaRoot + "service/" + className + "Service.java", service(basePackage, diagram, item));
            files.put(javaRoot + "controller/" + className + "Controller.java", controller(basePackage, diagram, item));

            trace.put(item.id().toString(), Map.of(
                "kind", "class",
                "name", className,
                "file", entityPath,
                "line", lineOf(files.get(entityPath), "@ModelElement(\"" + item.id() + "\")")
            ));

            for (var attr : item.attributes()) {
                trace.put(attr.id().toString(), Map.of(
                    "kind", "attribute",
                    "name", attr.name(),
                    "className", className,
                    "file", entityPath,
                    "line", lineOf(files.get(entityPath), "@ModelElement(\"" + attr.id() + "\")")
                ));
            }
        }

        // Associations in traceability
        for (var assoc : diagram.associations()) {
            var src = findClass(diagram, assoc.sourceId());
            var tgt = findClass(diagram, assoc.targetId());
            String targetFile = javaRoot + "model/" + javaName(src.name(), true) + ".java";
            int line = lineOf(files.get(targetFile), "@ModelElement(\"" + assoc.id() + "\")");
            if (line == 1) {
                targetFile = javaRoot + "model/" + javaName(tgt.name(), true) + ".java";
                line = lineOf(files.get(targetFile), "@ModelElement(\"" + assoc.id() + "\")");
            }
            trace.put(assoc.id().toString(), Map.of(
                "kind", "association",
                "name", assoc.name() != null ? assoc.name() : "rel_" + src.name() + "_" + tgt.name(),
                "sourceClass", src.name(),
                "targetClass", tgt.name(),
                "file", targetFile,
                "line", line
            ));
        }

        // Generalizations in traceability
        for (var gen : diagram.generalizations()) {
            var parent = findClass(diagram, gen.parentId());
            var child = findClass(diagram, gen.childId());
            String childPath = javaRoot + "model/" + javaName(child.name(), true) + ".java";
            trace.put(gen.id().toString(), Map.of(
                "kind", "generalization",
                "parent", parent.name(),
                "child", child.name(),
                "file", childPath,
                "line", lineOf(files.get(childPath), "extends " + javaName(parent.name(), true))
            ));
        }

        // 9. Traceability File
        try {
            files.put("model-traceability.json", mapper.writerWithDefaultPrettyPrinter().writeValueAsString(trace));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }

        // 10. Generated Tests
        files.put(testRoot + "ApplicationTests.java", applicationTests(basePackage));

        return zip(files);
    }

    private String pom(String groupId, String artifactId) {
        return """
        <?xml version="1.0" encoding="UTF-8"?>
        <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                 xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
          <modelVersion>4.0.0</modelVersion>
          <parent>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-parent</artifactId>
            <version>3.5.16</version>
            <relativePath/>
          </parent>
          <groupId>%s</groupId>
          <artifactId>%s</artifactId>
          <version>0.0.1-SNAPSHOT</version>
          <properties>
            <java.version>21</java.version>
            <testcontainers.version>1.21.3</testcontainers.version>
          </properties>
          <dependencies>
            <dependency>
              <groupId>org.springframework.boot</groupId>
              <artifactId>spring-boot-starter-web</artifactId>
            </dependency>
            <dependency>
              <groupId>org.springframework.boot</groupId>
              <artifactId>spring-boot-starter-data-jpa</artifactId>
            </dependency>
            <dependency>
              <groupId>org.springframework.boot</groupId>
              <artifactId>spring-boot-starter-security</artifactId>
            </dependency>
            <dependency>
              <groupId>org.springframework.boot</groupId>
              <artifactId>spring-boot-starter-validation</artifactId>
            </dependency>
            <dependency>
              <groupId>org.springframework.boot</groupId>
              <artifactId>spring-boot-starter-actuator</artifactId>
            </dependency>
            <dependency>
              <groupId>org.flywaydb</groupId>
              <artifactId>flyway-core</artifactId>
            </dependency>
            <dependency>
              <groupId>org.flywaydb</groupId>
              <artifactId>flyway-database-postgresql</artifactId>
            </dependency>
            <dependency>
              <groupId>org.postgresql</groupId>
              <artifactId>postgresql</artifactId>
              <scope>runtime</scope>
            </dependency>
            <dependency>
              <groupId>org.springdoc</groupId>
              <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
              <version>2.8.14</version>
            </dependency>
            <dependency>
              <groupId>org.springframework.boot</groupId>
              <artifactId>spring-boot-starter-test</artifactId>
              <scope>test</scope>
            </dependency>
            <dependency>
              <groupId>org.springframework.security</groupId>
              <artifactId>spring-security-test</artifactId>
              <scope>test</scope>
            </dependency>
            <dependency>
              <groupId>org.testcontainers</groupId>
              <artifactId>junit-jupiter</artifactId>
              <version>${testcontainers.version}</version>
              <scope>test</scope>
            </dependency>
            <dependency>
              <groupId>org.testcontainers</groupId>
              <artifactId>postgresql</artifactId>
              <version>${testcontainers.version}</version>
              <scope>test</scope>
            </dependency>
          </dependencies>
          <build>
            <plugins>
              <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
              </plugin>
            </plugins>
          </build>
        </project>
        """.formatted(groupId, artifactId);
    }

    private String applicationYml() {
        return """
        spring:
          datasource:
            url: ${DATABASE_URL:jdbc:postgresql://localhost:5432/app}
            username: ${DATABASE_USER:app}
            password: ${DATABASE_PASSWORD:app}
          jpa:
            hibernate:
              ddl-auto: validate
            open-in-view: false
            show-sql: false
        server:
          port: ${SERVER_PORT:8080}
        app:
          jwt:
            secret: ${JWT_SECRET:very-secret-jwt-key-for-development-purposes-only-32bytes}
            access-expiration-ms: 3600000
            refresh-expiration-ms: 604800000
        springdoc:
          api-docs:
            path: /v3/api-docs
          swagger-ui:
            path: /swagger-ui.html
        """;
    }

    private String dockerfile() {
        return """
        # Build stage
        FROM maven:3.9-eclipse-temurin-21 AS build
        WORKDIR /build
        COPY pom.xml .
        RUN mvn dependency:go-offline -B || true
        COPY src ./src
        RUN mvn clean package -DskipTests -B

        # Runtime stage
        FROM eclipse-temurin:21-jre
        WORKDIR /app
        COPY --from=build /build/target/*.jar app.jar
        EXPOSE 8080
        ENTRYPOINT ["java", "-jar", "app.jar"]
        """;
    }

    private String compose(String artifact) {
        return """
        services:
          db:
            image: postgres:17-alpine
            restart: unless-stopped
            environment:
              POSTGRES_DB: app
              POSTGRES_USER: app
              POSTGRES_PASSWORD: app
            ports:
              - "5432:5432"
            healthcheck:
              test: ["CMD-SHELL", "pg_isready -U app -d app"]
              interval: 5s
              timeout: 5s
              retries: 5

          api:
            build: .
            restart: unless-stopped
            ports:
              - "8080:8080"
            environment:
              DATABASE_URL: jdbc:postgresql://db:5432/app
              DATABASE_USER: app
              DATABASE_PASSWORD: app
              JWT_SECRET: very-secret-jwt-key-for-development-purposes-only-32bytes
              SERVER_PORT: 8080
            depends_on:
              db:
                condition: service_healthy
        """;
    }

    private String readme(DiagramDocument diagram, String artifactId) {
        return """
        # %s

        Backend Spring Boot generado automáticamente desde **Collab Modeler** (revisión %d).

        ## Características
        - **Java 21** y **Spring Boot 3.5.16**.
        - **PostgreSQL** con migraciones **Flyway**.
        - **JPA / Hibernate** con herencia `JOINED`, relaciones 1:1, 1:N, N:M con lado propietario y enumeraciones como `STRING`.
        - **DTOs separados** de entrada y salida con **Bean Validation** y **Mappers**.
        - **CRUD completo** con actualización para todas las entidades.
        - **OpenAPI 3.0.3** y Swagger UI en `/swagger-ui.html`.
        - **Autenticación completa**: registro con correo/contraseña, JWT de acceso y refresco, roles `ADMIN` y `USER`. El primer usuario registrado obtiene automáticamente el rol `ADMIN`.
        - Esquema de autenticación aislado en tablas `_app_auth_*` para evitar colisiones con el modelo de dominio.
        - **Dockerfile multi-stage** y **Docker Compose**.
        - Trazabilidad hacia el diagrama UML con anotación `@ModelElement` y `model-traceability.json`.

        ## Ejecución con Docker Compose
        ```bash
        docker compose up --build
        ```
        La API estará disponible en `http://localhost:8080` y la base de datos en `localhost:5432`.

        ## Ejecución local con Maven
        1. Inicia PostgreSQL (o `docker compose up -d db`).
        2. Configura las variables si es necesario:
        ```bash
        export DATABASE_URL=jdbc:postgresql://localhost:5432/app
        export DATABASE_USER=app
        export DATABASE_PASSWORD=app
        ```
        3. Ejecuta la aplicación:
        ```bash
        mvn spring-boot:run
        ```

        ## Pruebas
        ```bash
        mvn test
        ```
        Las pruebas de integración ejecutan PostgreSQL mediante Testcontainers.

        ## Autenticación
        - Registro: `POST /api/auth/register` con JSON `{"email":"admin@example.com","password":"password123","fullName":"Administrador"}`. El primer usuario registrado es `ROLE_ADMIN`.
        - Inicio de sesión: `POST /api/auth/login` con JSON `{"email":"...","password":"..."}`.
        - Refresco de token: `POST /api/auth/refresh` con `{"refreshToken":"..."}`.
        - Perfil: `GET /api/auth/me` con cabecera `Authorization: Bearer <accessToken>`.
        """.formatted(diagram.name(), diagram.revision());
    }

    private String application(String pkg) {
        return """
        package %s;

        import org.springframework.boot.SpringApplication;
        import org.springframework.boot.autoconfigure.SpringBootApplication;

        @SpringBootApplication
        public class Application {
            public static void main(String[] args) {
                SpringApplication.run(Application.class, args);
            }
        }
        """.formatted(pkg);
    }

    private String modelElementAnnotation(String pkg) {
        return """
        package %s.model;

        import java.lang.annotation.*;

        @Retention(RetentionPolicy.RUNTIME)
        @Target({ElementType.TYPE, ElementType.FIELD, ElementType.METHOD})
        public @interface ModelElement {
            String value();
        }
        """.formatted(pkg);
    }

    private String enumeration(String pkg, DiagramDocument.Enumeration en) {
        String name = javaName(en.name(), true);
        StringBuilder sb = new StringBuilder();
        sb.append("package ").append(pkg).append(".model;\n\n");
        sb.append("@ModelElement(\"").append(en.id()).append("\")\n");
        sb.append("public enum ").append(name).append(" {\n");
        for (int i = 0; i < en.values().size(); i++) {
            sb.append("    ").append(en.values().get(i).name());
            sb.append(i + 1 == en.values().size() ? "\n" : ",\n");
        }
        sb.append("}\n");
        return sb.toString();
    }

    private String entity(String pkg, DiagramDocument diagram, DiagramDocument.ClassElement item) {
        String name = javaName(item.name(), true);
        UUID parentId = findParentId(diagram, item.id());
        boolean hasParent = parentId != null;
        boolean isParent = isParentClass(diagram, item.id());
        String parentName = hasParent ? javaName(findClass(diagram, parentId).name(), true) : null;

        StringBuilder sb = new StringBuilder();
        sb.append("package ").append(pkg).append(".model;\n\n");
        sb.append("import jakarta.persistence.*;\n");
        sb.append("import java.math.BigDecimal;\n");
        sb.append("import java.time.*;\n");
        sb.append("import java.util.*;\n\n");

        sb.append("@Entity\n");
        sb.append("@Table(name = \"").append(sqlName(item.name())).append("\")\n");
        if (isExplicitJoinClass(diagram, item)) sb.append("@IdClass(").append(name).append("Id.class)\n");
        if (isParent) {
            sb.append("@Inheritance(strategy = InheritanceType.JOINED)\n");
        }
        if (hasParent) {
            sb.append("@PrimaryKeyJoinColumn(name = \"id\")\n");
        }
        sb.append("@ModelElement(\"").append(item.id()).append("\")\n");
        sb.append("public class ").append(name);
        if (hasParent) {
            sb.append(" extends ").append(parentName);
        }
        sb.append(" {\n");

        // Attributes
        for (var attr : item.attributes()) {
            if (attr.primaryKey() && !hasParent) {
                sb.append("    @Id\n");
            }
            sb.append("    @ModelElement(\"").append(attr.id()).append("\")\n");
            if (isEnum(diagram, attr.type())) {
                sb.append("    @Enumerated(EnumType.STRING)\n");
            }
            sb.append("    @Column(name = \"").append(sqlName(attr.name())).append("\", nullable = ")
              .append(!attr.required()).append(", unique = ").append(attr.unique()).append(")\n");
            sb.append("    private ").append(javaType(diagram, attr.type())).append(" ")
              .append(javaName(attr.name(), false)).append(";\n\n");
        }

        // Associations
        for (var assoc : diagram.associations()) {
            if (assoc.sourceId().equals(item.id())) {
                var target = findClass(diagram, assoc.targetId());
                String targetClassName = javaName(target.name(), true);
                String fieldName = javaName(target.name(), false);

                if (isOneToOne(assoc.sourceCardinality(), assoc.targetCardinality())) {
                    boolean isOwning = "SOURCE".equalsIgnoreCase(assoc.owningSide());
                    sb.append("    @ModelElement(\"").append(assoc.id()).append("\")\n");
                    if (isOwning) {
                        sb.append("    @OneToOne(fetch = FetchType.LAZY)\n");
                        sb.append("    @JoinColumn(name = \"").append(sqlName(target.name())).append("_id\", unique = true)\n");
                    } else {
                        sb.append("    @OneToOne(mappedBy = \"").append(javaName(item.name(), false)).append("\", fetch = FetchType.LAZY)\n");
                    }
                    sb.append("    private ").append(targetClassName).append(" ").append(fieldName).append(";\n\n");
                } else if (isManyToOne(assoc.sourceCardinality(), assoc.targetCardinality())) {
                    sb.append("    @ModelElement(\"").append(assoc.id()).append("\")\n");
                    sb.append("    @ManyToOne(fetch = FetchType.LAZY)\n");
                    sb.append("    @JoinColumn(name = \"").append(sqlName(target.name())).append("_id\")\n");
                    sb.append("    private ").append(targetClassName).append(" ").append(fieldName).append(";\n\n");
                } else if (isOneToMany(assoc.sourceCardinality(), assoc.targetCardinality())) {
                    sb.append("    @ModelElement(\"").append(assoc.id()).append("\")\n");
                    sb.append("    @OneToMany(mappedBy = \"").append(javaName(item.name(), false)).append("\", cascade = CascadeType.ALL)\n");
                    sb.append("    private List<").append(targetClassName).append("> ").append(plural(fieldName)).append(" = new ArrayList<>();\n\n");
                } else if (isManyToMany(assoc.sourceCardinality(), assoc.targetCardinality())) {
                    boolean isOwning = !"TARGET".equalsIgnoreCase(assoc.owningSide());
                    sb.append("    @ModelElement(\"").append(assoc.id()).append("\")\n");
                    if (isOwning) {
                        sb.append("    @ManyToMany(fetch = FetchType.LAZY)\n");
                        sb.append("    @JoinTable(name = \"").append(sqlName(item.name())).append("_").append(sqlName(target.name())).append("\",\n");
                        sb.append("        joinColumns = @JoinColumn(name = \"").append(sqlName(item.name())).append("_id\"),\n");
                        sb.append("        inverseJoinColumns = @JoinColumn(name = \"").append(sqlName(target.name())).append("_id\"))\n");
                    } else {
                        sb.append("    @ManyToMany(mappedBy = \"").append(plural(javaName(item.name(), false))).append("\", fetch = FetchType.LAZY)\n");
                    }
                    sb.append("    private Set<").append(targetClassName).append("> ").append(plural(fieldName)).append(" = new HashSet<>();\n\n");
                }
            } else if (assoc.targetId().equals(item.id())) {
                var source = findClass(diagram, assoc.sourceId());
                String sourceClassName = javaName(source.name(), true);
                String fieldName = javaName(source.name(), false);

                if (isOneToOne(assoc.sourceCardinality(), assoc.targetCardinality())) {
                    boolean isOwning = "TARGET".equalsIgnoreCase(assoc.owningSide());
                    sb.append("    @ModelElement(\"").append(assoc.id()).append("\")\n");
                    if (isOwning) {
                        sb.append("    @OneToOne(fetch = FetchType.LAZY)\n");
                        sb.append("    @JoinColumn(name = \"").append(sqlName(source.name())).append("_id\", unique = true)\n");
                    } else {
                        sb.append("    @OneToOne(mappedBy = \"").append(javaName(item.name(), false)).append("\", fetch = FetchType.LAZY)\n");
                    }
                    sb.append("    private ").append(sourceClassName).append(" ").append(fieldName).append(";\n\n");
                } else if (isOneToMany(assoc.sourceCardinality(), assoc.targetCardinality())) {
                    sb.append("    @ModelElement(\"").append(assoc.id()).append("\")\n");
                    sb.append("    @ManyToOne(fetch = FetchType.LAZY)\n");
                    sb.append("    @JoinColumn(name = \"").append(sqlName(source.name())).append("_id\"");
                    if (isExplicitJoinClass(diagram, item)) sb.append(", insertable = false, updatable = false");
                    sb.append(")\n");
                    sb.append("    private ").append(sourceClassName).append(" ").append(fieldName).append(";\n\n");
                } else if (isManyToOne(assoc.sourceCardinality(), assoc.targetCardinality())) {
                    sb.append("    @ModelElement(\"").append(assoc.id()).append("\")\n");
                    sb.append("    @OneToMany(mappedBy = \"").append(javaName(item.name(), false)).append("\", cascade = CascadeType.ALL)\n");
                    sb.append("    private List<").append(sourceClassName).append("> ").append(plural(fieldName)).append(" = new ArrayList<>();\n\n");
                } else if (isManyToMany(assoc.sourceCardinality(), assoc.targetCardinality())) {
                    boolean isOwning = "TARGET".equalsIgnoreCase(assoc.owningSide());
                    sb.append("    @ModelElement(\"").append(assoc.id()).append("\")\n");
                    if (isOwning) {
                        sb.append("    @ManyToMany(fetch = FetchType.LAZY)\n");
                        sb.append("    @JoinTable(name = \"").append(sqlName(source.name())).append("_").append(sqlName(item.name())).append("\",\n");
                        sb.append("        joinColumns = @JoinColumn(name = \"").append(sqlName(item.name())).append("_id\"),\n");
                        sb.append("        inverseJoinColumns = @JoinColumn(name = \"").append(sqlName(source.name())).append("_id\"))\n");
                    } else {
                        sb.append("    @ManyToMany(mappedBy = \"").append(plural(javaName(item.name(), false))).append("\", fetch = FetchType.LAZY)\n");
                    }
                    sb.append("    private Set<").append(sourceClassName).append("> ").append(plural(fieldName)).append(" = new HashSet<>();\n\n");
                }
            }
        }

        // Default constructor
        sb.append("    public ").append(name).append("() {}\n\n");

        // Getters and Setters for Attributes
        for (var attr : item.attributes()) {
            String field = javaName(attr.name(), false);
            String type = javaType(diagram, attr.type());
            String cap = javaName(attr.name(), true);
            sb.append("    public ").append(type).append(" get").append(cap).append("() { return ").append(field).append("; }\n");
            sb.append("    public void set").append(cap).append("(").append(type).append(" value) { this.").append(field).append(" = value; }\n");
        }

        // Getters and Setters for Associations
        for (var assoc : diagram.associations()) {
            if (assoc.sourceId().equals(item.id())) {
                var target = findClass(diagram, assoc.targetId());
                String targetClassName = javaName(target.name(), true);
                String fieldName = javaName(target.name(), false);
                if (isOneToOne(assoc.sourceCardinality(), assoc.targetCardinality()) || isManyToOne(assoc.sourceCardinality(), assoc.targetCardinality())) {
                    String cap = javaName(target.name(), true);
                    sb.append("    public ").append(targetClassName).append(" get").append(cap).append("() { return ").append(fieldName).append("; }\n");
                    sb.append("    public void set").append(cap).append("(").append(targetClassName).append(" value) { this.").append(fieldName).append(" = value; }\n");
                } else if (isOneToMany(assoc.sourceCardinality(), assoc.targetCardinality())) {
                    String cap = plural(javaName(target.name(), true));
                    sb.append("    public List<").append(targetClassName).append("> get").append(cap).append("() { return ").append(plural(fieldName)).append("; }\n");
                    sb.append("    public void set").append(cap).append("(List<").append(targetClassName).append("> value) { this.").append(plural(fieldName)).append(" = value; }\n");
                } else if (isManyToMany(assoc.sourceCardinality(), assoc.targetCardinality())) {
                    String cap = plural(javaName(target.name(), true));
                    sb.append("    public Set<").append(targetClassName).append("> get").append(cap).append("() { return ").append(plural(fieldName)).append("; }\n");
                    sb.append("    public void set").append(cap).append("(Set<").append(targetClassName).append("> value) { this.").append(plural(fieldName)).append(" = value; }\n");
                }
            } else if (assoc.targetId().equals(item.id())) {
                var source = findClass(diagram, assoc.sourceId());
                String sourceClassName = javaName(source.name(), true);
                String fieldName = javaName(source.name(), false);
                if (isOneToOne(assoc.sourceCardinality(), assoc.targetCardinality()) || isOneToMany(assoc.sourceCardinality(), assoc.targetCardinality())) {
                    String cap = javaName(source.name(), true);
                    sb.append("    public ").append(sourceClassName).append(" get").append(cap).append("() { return ").append(fieldName).append("; }\n");
                    sb.append("    public void set").append(cap).append("(").append(sourceClassName).append(" value) { this.").append(fieldName).append(" = value; }\n");
                } else if (isManyToOne(assoc.sourceCardinality(), assoc.targetCardinality())) {
                    String cap = plural(javaName(source.name(), true));
                    sb.append("    public List<").append(sourceClassName).append("> get").append(cap).append("() { return ").append(plural(fieldName)).append("; }\n");
                    sb.append("    public void set").append(cap).append("(List<").append(sourceClassName).append("> value) { this.").append(plural(fieldName)).append(" = value; }\n");
                } else if (isManyToMany(assoc.sourceCardinality(), assoc.targetCardinality())) {
                    String cap = plural(javaName(source.name(), true));
                    sb.append("    public Set<").append(sourceClassName).append("> get").append(cap).append("() { return ").append(plural(fieldName)).append("; }\n");
                    sb.append("    public void set").append(cap).append("(Set<").append(sourceClassName).append("> value) { this.").append(plural(fieldName)).append(" = value; }\n");
                }
            }
        }

        sb.append("}\n");
        return sb.toString();
    }

    private String inputDto(String pkg, DiagramDocument diagram, DiagramDocument.ClassElement item) {
        String name = javaName(item.name(), true);
        StringBuilder sb = new StringBuilder();
        sb.append("package ").append(pkg).append(".dto;\n\n");
        sb.append("import jakarta.validation.constraints.*;\n");
        sb.append("import java.math.BigDecimal;\n");
        sb.append("import java.time.*;\n");
        sb.append("import java.util.*;\n");
        sb.append("import ").append(pkg).append(".model.*;\n\n");

        sb.append("public class ").append(name).append("InputDto {\n");

        for (var attr : item.attributes()) {
            if (attr.primaryKey()) continue;
            String type = javaType(diagram, attr.type());
            String field = javaName(attr.name(), false);

            if (attr.required()) {
                if ("String".equals(type) || "Text".equals(type)) {
                    sb.append("    @NotBlank(message = \"").append(field).append(" es obligatorio\")\n");
                } else {
                    sb.append("    @NotNull(message = \"").append(field).append(" es obligatorio\")\n");
                }
            }
            if ("String".equals(type)) {
                sb.append("    @Size(max = 255)\n");
            }
            sb.append("    private ").append(type).append(" ").append(field).append(";\n\n");
        }

        // Relationship IDs
        for (var assoc : diagram.associations()) {
            if (assoc.sourceId().equals(item.id())) {
                var target = findClass(diagram, assoc.targetId());
                String idType = idType(diagram, target);
                if (isManyToOne(assoc.sourceCardinality(), assoc.targetCardinality()) || isOneToOne(assoc.sourceCardinality(), assoc.targetCardinality())) {
                    sb.append("    private ").append(idType).append(" ").append(javaName(target.name(), false)).append("Id;\n\n");
                } else if (isManyToMany(assoc.sourceCardinality(), assoc.targetCardinality())) {
                    sb.append("    private Set<").append(idType).append("> ").append(javaName(target.name(), false)).append("Ids = new HashSet<>();\n\n");
                }
            } else if (assoc.targetId().equals(item.id())) {
                var source = findClass(diagram, assoc.sourceId());
                String idType = idType(diagram, source);
                if (isOneToMany(assoc.sourceCardinality(), assoc.targetCardinality())) {
                    sb.append("    private ").append(idType).append(" ").append(javaName(source.name(), false)).append("Id;\n\n");
                }
            }
        }

        // Getters and Setters
        for (var attr : item.attributes()) {
            if (attr.primaryKey()) continue;
            String type = javaType(diagram, attr.type());
            String field = javaName(attr.name(), false);
            String cap = javaName(attr.name(), true);
            sb.append("    public ").append(type).append(" get").append(cap).append("() { return ").append(field).append("; }\n");
            sb.append("    public void set").append(cap).append("(").append(type).append(" value) { this.").append(field).append(" = value; }\n");
        }

        for (var assoc : diagram.associations()) {
            if (assoc.sourceId().equals(item.id())) {
                var target = findClass(diagram, assoc.targetId());
                String idType = idType(diagram, target);
                if (isManyToOne(assoc.sourceCardinality(), assoc.targetCardinality()) || isOneToOne(assoc.sourceCardinality(), assoc.targetCardinality())) {
                    String cap = javaName(target.name(), true) + "Id";
                    String field = javaName(target.name(), false) + "Id";
                    sb.append("    public ").append(idType).append(" get").append(cap).append("() { return ").append(field).append("; }\n");
                    sb.append("    public void set").append(cap).append("(").append(idType).append(" value) { this.").append(field).append(" = value; }\n");
                } else if (isManyToMany(assoc.sourceCardinality(), assoc.targetCardinality())) {
                    String cap = javaName(target.name(), true) + "Ids";
                    String field = javaName(target.name(), false) + "Ids";
                    sb.append("    public Set<").append(idType).append("> get").append(cap).append("() { return ").append(field).append("; }\n");
                    sb.append("    public void set").append(cap).append("(Set<").append(idType).append("> value) { this.").append(field).append(" = value; }\n");
                }
            } else if (assoc.targetId().equals(item.id())) {
                var source = findClass(diagram, assoc.sourceId());
                String idType = idType(diagram, source);
                if (isOneToMany(assoc.sourceCardinality(), assoc.targetCardinality())) {
                    String cap = javaName(source.name(), true) + "Id";
                    String field = javaName(source.name(), false) + "Id";
                    sb.append("    public ").append(idType).append(" get").append(cap).append("() { return ").append(field).append("; }\n");
                    sb.append("    public void set").append(cap).append("(").append(idType).append(" value) { this.").append(field).append(" = value; }\n");
                }
            }
        }

        sb.append("}\n");
        return sb.toString();
    }

    private String outputDto(String pkg, DiagramDocument diagram, DiagramDocument.ClassElement item) {
        String name = javaName(item.name(), true);
        StringBuilder sb = new StringBuilder();
        sb.append("package ").append(pkg).append(".dto;\n\n");
        sb.append("import java.math.BigDecimal;\n");
        sb.append("import java.time.*;\n");
        sb.append("import java.util.*;\n");
        sb.append("import ").append(pkg).append(".model.*;\n\n");

        sb.append("public class ").append(name).append("OutputDto {\n");

        // Primary key if inherited
        UUID parentId = findParentId(diagram, item.id());
        if (parentId != null && item.attributes().stream().noneMatch(DiagramDocument.Attribute::primaryKey)) {
            var parent = findClass(diagram, parentId);
            String idType = idType(diagram, parent);
            sb.append("    private ").append(idType).append(" id;\n");
        }

        for (var attr : item.attributes()) {
            sb.append("    private ").append(javaType(diagram, attr.type())).append(" ").append(javaName(attr.name(), false)).append(";\n");
        }

        // Relationship references in output
        for (var assoc : diagram.associations()) {
            if (assoc.sourceId().equals(item.id())) {
                var target = findClass(diagram, assoc.targetId());
                String idType = idType(diagram, target);
                if (isManyToOne(assoc.sourceCardinality(), assoc.targetCardinality()) || isOneToOne(assoc.sourceCardinality(), assoc.targetCardinality())) {
                    sb.append("    private ").append(idType).append(" ").append(javaName(target.name(), false)).append("Id;\n");
                } else if (isManyToMany(assoc.sourceCardinality(), assoc.targetCardinality())) {
                    sb.append("    private Set<").append(idType).append("> ").append(javaName(target.name(), false)).append("Ids = new HashSet<>();\n");
                }
            } else if (assoc.targetId().equals(item.id())) {
                var source = findClass(diagram, assoc.sourceId());
                String idType = idType(diagram, source);
                if (isOneToMany(assoc.sourceCardinality(), assoc.targetCardinality())) {
                    sb.append("    private ").append(idType).append(" ").append(javaName(source.name(), false)).append("Id;\n");
                }
            }
        }

        sb.append("\n    public ").append(name).append("OutputDto() {}\n\n");

        if (parentId != null && item.attributes().stream().noneMatch(DiagramDocument.Attribute::primaryKey)) {
            var parent = findClass(diagram, parentId);
            String idType = idType(diagram, parent);
            sb.append("    public ").append(idType).append(" getId() { return id; }\n");
            sb.append("    public void setId(").append(idType).append(" value) { this.id = value; }\n");
        }

        for (var attr : item.attributes()) {
            String type = javaType(diagram, attr.type());
            String field = javaName(attr.name(), false);
            String cap = javaName(attr.name(), true);
            sb.append("    public ").append(type).append(" get").append(cap).append("() { return ").append(field).append("; }\n");
            sb.append("    public void set").append(cap).append("(").append(type).append(" value) { this.").append(field).append(" = value; }\n");
        }

        for (var assoc : diagram.associations()) {
            if (assoc.sourceId().equals(item.id())) {
                var target = findClass(diagram, assoc.targetId());
                String idType = idType(diagram, target);
                if (isManyToOne(assoc.sourceCardinality(), assoc.targetCardinality()) || isOneToOne(assoc.sourceCardinality(), assoc.targetCardinality())) {
                    String cap = javaName(target.name(), true) + "Id";
                    String field = javaName(target.name(), false) + "Id";
                    sb.append("    public ").append(idType).append(" get").append(cap).append("() { return ").append(field).append("; }\n");
                    sb.append("    public void set").append(cap).append("(").append(idType).append(" value) { this.").append(field).append(" = value; }\n");
                } else if (isManyToMany(assoc.sourceCardinality(), assoc.targetCardinality())) {
                    String cap = javaName(target.name(), true) + "Ids";
                    String field = javaName(target.name(), false) + "Ids";
                    sb.append("    public Set<").append(idType).append("> get").append(cap).append("() { return ").append(field).append("; }\n");
                    sb.append("    public void set").append(cap).append("(Set<").append(idType).append("> value) { this.").append(field).append(" = value; }\n");
                }
            } else if (assoc.targetId().equals(item.id())) {
                var source = findClass(diagram, assoc.sourceId());
                String idType = idType(diagram, source);
                if (isOneToMany(assoc.sourceCardinality(), assoc.targetCardinality())) {
                    String cap = javaName(source.name(), true) + "Id";
                    String field = javaName(source.name(), false) + "Id";
                    sb.append("    public ").append(idType).append(" get").append(cap).append("() { return ").append(field).append("; }\n");
                    sb.append("    public void set").append(cap).append("(").append(idType).append(" value) { this.").append(field).append(" = value; }\n");
                }
            }
        }

        sb.append("}\n");
        return sb.toString();
    }

    private String mapper(String pkg, DiagramDocument diagram, DiagramDocument.ClassElement item) {
        String name = javaName(item.name(), true);
        StringBuilder sb = new StringBuilder();
        sb.append("package ").append(pkg).append(".mapper;\n\n");
        sb.append("import ").append(pkg).append(".dto.*;\n");
        sb.append("import ").append(pkg).append(".model.*;\n");
        sb.append("import ").append(pkg).append(".repository.*;\n");
        sb.append("import org.springframework.stereotype.Component;\n");
        sb.append("import java.util.*;\n\n");

        sb.append("@Component\n");
        sb.append("public class ").append(name).append("Mapper {\n");

        // Repositories injection for resolving relations
        Set<String> neededRepos = new LinkedHashSet<>();
        for (var assoc : diagram.associations()) {
            if (assoc.sourceId().equals(item.id())) {
                var target = findClass(diagram, assoc.targetId());
                neededRepos.add(javaName(target.name(), true));
            } else if (assoc.targetId().equals(item.id())) {
                var source = findClass(diagram, assoc.sourceId());
                if (isOneToMany(assoc.sourceCardinality(), assoc.targetCardinality())) {
                    neededRepos.add(javaName(source.name(), true));
                }
            }
        }

        for (var repoClass : neededRepos) {
            sb.append("    private final ").append(repoClass).append("Repository ").append(javaName(repoClass, false)).append("Repository;\n");
        }

        if (!neededRepos.isEmpty()) {
            sb.append("    public ").append(name).append("Mapper(");
            List<String> params = new ArrayList<>();
            for (var repoClass : neededRepos) {
                params.add(repoClass + "Repository " + javaName(repoClass, false) + "Repository");
            }
            sb.append(String.join(", ", params)).append(") {\n");
            for (var repoClass : neededRepos) {
                sb.append("        this.").append(javaName(repoClass, false)).append("Repository = ").append(javaName(repoClass, false)).append("Repository;\n");
            }
            sb.append("    }\n\n");
        }

        // toEntity
        sb.append("    public ").append(name).append(" toEntity(").append(name).append("InputDto dto) {\n");
        sb.append("        ").append(name).append(" entity = new ").append(name).append("();\n");
        String pkField = getPkFieldName(diagram, item);
        String pkType = idType(diagram, item);
        if ("UUID".equals(pkType) && !isExplicitJoinClass(diagram, item)) {
            sb.append("        entity.set").append(javaName(pkField, true)).append("(UUID.randomUUID());\n");
        }
        if (isExplicitJoinClass(diagram, item)) {
            for (var assoc : diagram.associations()) {
                if (!assoc.targetId().equals(item.id()) || !isOneToMany(assoc.sourceCardinality(), assoc.targetCardinality())) continue;
                var source = findClass(diagram, assoc.sourceId());
                sb.append("        entity.set").append(javaName(source.name() + "_id", true)).append("(dto.get")
                    .append(javaName(source.name(), true)).append("Id());\n");
            }
        }
        sb.append("        updateEntity(entity, dto);\n");
        sb.append("        return entity;\n");
        sb.append("    }\n\n");

        // updateEntity
        sb.append("    public void updateEntity(").append(name).append(" entity, ").append(name).append("InputDto dto) {\n");
        for (var attr : item.attributes()) {
            if (attr.primaryKey()) continue;
            String cap = javaName(attr.name(), true);
            sb.append("        entity.set").append(cap).append("(dto.get").append(cap).append("());\n");
        }

        for (var assoc : diagram.associations()) {
            if (assoc.sourceId().equals(item.id())) {
                var target = findClass(diagram, assoc.targetId());
                String targetCap = javaName(target.name(), true);
                String targetVar = javaName(target.name(), false);
                if (isManyToOne(assoc.sourceCardinality(), assoc.targetCardinality()) || isOneToOne(assoc.sourceCardinality(), assoc.targetCardinality())) {
                    sb.append("        if (dto.get").append(targetCap).append("Id() != null) {\n");
                    sb.append("            entity.set").append(targetCap).append("(").append(targetVar).append("Repository.findById(dto.get").append(targetCap).append("Id()).orElse(null));\n");
                    sb.append("        } else { entity.set").append(targetCap).append("(null); }\n");
                } else if (isManyToMany(assoc.sourceCardinality(), assoc.targetCardinality())) {
                    sb.append("        if (dto.get").append(targetCap).append("Ids() != null) {\n");
                    sb.append("            entity.set").append(plural(targetCap)).append("(new HashSet<>(").append(targetVar).append("Repository.findAllById(dto.get").append(targetCap).append("Ids())));\n");
                    sb.append("        }\n");
                }
            } else if (assoc.targetId().equals(item.id())) {
                var source = findClass(diagram, assoc.sourceId());
                String sourceCap = javaName(source.name(), true);
                String sourceVar = javaName(source.name(), false);
                if (isOneToMany(assoc.sourceCardinality(), assoc.targetCardinality())) {
                    sb.append("        if (dto.get").append(sourceCap).append("Id() != null) {\n");
                    sb.append("            entity.set").append(sourceCap).append("(").append(sourceVar).append("Repository.findById(dto.get").append(sourceCap).append("Id()).orElse(null));\n");
                    sb.append("        } else { entity.set").append(sourceCap).append("(null); }\n");
                }
            }
        }
        sb.append("    }\n\n");

        // toOutputDto
        sb.append("    public ").append(name).append("OutputDto toOutputDto(").append(name).append(" entity) {\n");
        sb.append("        ").append(name).append("OutputDto dto = new ").append(name).append("OutputDto();\n");
        UUID parentId = findParentId(diagram, item.id());
        if (parentId != null && item.attributes().stream().noneMatch(DiagramDocument.Attribute::primaryKey)) {
            sb.append("        dto.setId(entity.getId());\n");
        }
        for (var attr : item.attributes()) {
            String cap = javaName(attr.name(), true);
            sb.append("        dto.set").append(cap).append("(entity.get").append(cap).append("());\n");
        }

        for (var assoc : diagram.associations()) {
            if (assoc.sourceId().equals(item.id())) {
                var target = findClass(diagram, assoc.targetId());
                String targetCap = javaName(target.name(), true);
                String targetVar = javaName(target.name(), false);
                String targetPkField = getPkFieldName(diagram, target);
                if (isManyToOne(assoc.sourceCardinality(), assoc.targetCardinality()) || isOneToOne(assoc.sourceCardinality(), assoc.targetCardinality())) {
                    sb.append("        if (entity.get").append(targetCap).append("() != null) {\n");
                    sb.append("            dto.set").append(targetCap).append("Id(entity.get").append(targetCap).append("().get").append(javaName(targetPkField, true)).append("());\n");
                    sb.append("        }\n");
                } else if (isManyToMany(assoc.sourceCardinality(), assoc.targetCardinality())) {
                    sb.append("        if (entity.get").append(plural(targetCap)).append("() != null) {\n");
                    sb.append("            Set<").append(idType(diagram, target)).append("> ids = new HashSet<>();\n");
                    sb.append("            for (var itemRel : entity.get").append(plural(targetCap)).append("()) { ids.add(itemRel.get").append(javaName(targetPkField, true)).append("()); }\n");
                    sb.append("            dto.set").append(targetCap).append("Ids(ids);\n");
                    sb.append("        }\n");
                }
            } else if (assoc.targetId().equals(item.id())) {
                var source = findClass(diagram, assoc.sourceId());
                String sourceCap = javaName(source.name(), true);
                String sourcePkField = getPkFieldName(diagram, source);
                if (isOneToMany(assoc.sourceCardinality(), assoc.targetCardinality())) {
                    sb.append("        if (entity.get").append(sourceCap).append("() != null) {\n");
                    sb.append("            dto.set").append(sourceCap).append("Id(entity.get").append(sourceCap).append("().get").append(javaName(sourcePkField, true)).append("());\n");
                    sb.append("        }\n");
                }
            }
        }
        sb.append("        return dto;\n");
        sb.append("    }\n");

        sb.append("}\n");
        return sb.toString();
    }

    private String repository(String pkg, DiagramDocument diagram, DiagramDocument.ClassElement item) {
        String name = javaName(item.name(), true);
        String idType = idType(diagram, item);
        return """
        package %s.repository;

        import %s.model.*;
        import org.springframework.data.jpa.repository.JpaRepository;
        import java.math.BigDecimal;
        import java.time.*;
        import java.util.UUID;

        public interface %sRepository extends JpaRepository<%s, %s> {}
        """.formatted(pkg, pkg, name, name, idType);
    }

    private String service(String pkg, DiagramDocument diagram, DiagramDocument.ClassElement item) {
        String name = javaName(item.name(), true);
        String idType = idType(diagram, item);
        return "package " + pkg + ".service;\n\n" +
            "import " + pkg + ".dto." + name + "InputDto;\n" +
            "import " + pkg + ".dto." + name + "OutputDto;\n" +
            "import " + pkg + ".error.ResourceNotFoundException;\n" +
            "import " + pkg + ".mapper." + name + "Mapper;\n" +
            "import " + pkg + ".model.*;\n" +
            "import " + pkg + ".repository." + name + "Repository;\n" +
            "import org.springframework.stereotype.Service;\n" +
            "import org.springframework.transaction.annotation.Transactional;\n" +
            "import java.math.BigDecimal;\n" +
            "import java.time.*;\n" +
            "import java.util.*;\n\n" +
            "@Service\n" +
            "@Transactional\n" +
            "public class " + name + "Service {\n" +
            "    private final " + name + "Repository repository;\n" +
            "    private final " + name + "Mapper mapper;\n\n" +
            "    public " + name + "Service(" + name + "Repository repository, " + name + "Mapper mapper) {\n" +
            "        this.repository = repository;\n" +
            "        this.mapper = mapper;\n" +
            "    }\n\n" +
            "    @Transactional(readOnly = true)\n" +
            "    public List<" + name + "OutputDto> findAll() {\n" +
            "        return repository.findAll().stream().map(mapper::toOutputDto).toList();\n" +
            "    }\n\n" +
            "    @Transactional(readOnly = true)\n" +
            "    public " + name + "OutputDto findById(" + idType + " id) {\n" +
            "        return repository.findById(id)\n" +
            "            .map(mapper::toOutputDto)\n" +
            "            .orElseThrow(() -> new ResourceNotFoundException(\"" + name + " no encontrado con id: \" + id));\n" +
            "    }\n\n" +
            "    public " + name + "OutputDto create(" + name + "InputDto input) {\n" +
            "        " + name + " entity = mapper.toEntity(input);\n" +
            "        " + name + " saved = repository.save(entity);\n" +
            "        return mapper.toOutputDto(saved);\n" +
            "    }\n\n" +
            "    public " + name + "OutputDto update(" + idType + " id, " + name + "InputDto input) {\n" +
            "        " + name + " entity = repository.findById(id)\n" +
            "            .orElseThrow(() -> new ResourceNotFoundException(\"" + name + " no encontrado con id: \" + id));\n" +
            "        mapper.updateEntity(entity, input);\n" +
            "        " + name + " updated = repository.save(entity);\n" +
            "        return mapper.toOutputDto(updated);\n" +
            "    }\n\n" +
            "    public void deleteById(" + idType + " id) {\n" +
            "        if (!repository.existsById(id)) {\n" +
            "            throw new ResourceNotFoundException(\"" + name + " no encontrado con id: \" + id);\n" +
            "        }\n" +
            "        repository.deleteById(id);\n" +
            "    }\n" +
            "}\n";
    }

    private String controller(String pkg, DiagramDocument diagram, DiagramDocument.ClassElement item) {
        String name = javaName(item.name(), true);
        String idType = idType(diagram, item);
        String resource = sqlName(item.name());
        return "package " + pkg + ".controller;\n\n" +
            "import " + pkg + ".dto." + name + "InputDto;\n" +
            "import " + pkg + ".dto." + name + "OutputDto;\n" +
            "import " + pkg + ".service." + name + "Service;\n" +
            "import " + pkg + ".model.*;\n" +
            "import jakarta.validation.Valid;\n" +
            "import org.springframework.http.HttpStatus;\n" +
            "import org.springframework.web.bind.annotation.*;\n" +
            "import java.math.BigDecimal;\n" +
            "import java.time.*;\n" +
            "import java.util.*;\n\n" +
            "@RestController\n" +
            "@RequestMapping(\"/api/" + resource + "\")\n" +
            "public class " + name + "Controller {\n" +
            "    private final " + name + "Service service;\n\n" +
            "    public " + name + "Controller(" + name + "Service service) {\n" +
            "        this.service = service;\n" +
            "    }\n\n" +
            "    @GetMapping\n" +
            "    public List<" + name + "OutputDto> findAll() {\n" +
            "        return service.findAll();\n" +
            "    }\n\n" +
            "    @GetMapping(\"/{id}\")\n" +
            "    public " + name + "OutputDto findById(@PathVariable " + idType + " id) {\n" +
            "        return service.findById(id);\n" +
            "    }\n\n" +
            "    @PostMapping\n" +
            "    @ResponseStatus(HttpStatus.CREATED)\n" +
            "    public " + name + "OutputDto create(@Valid @RequestBody " + name + "InputDto input) {\n" +
            "        return service.create(input);\n" +
            "    }\n\n" +
            "    @PutMapping(\"/{id}\")\n" +
            "    public " + name + "OutputDto update(@PathVariable " + idType + " id, @Valid @RequestBody " + name + "InputDto input) {\n" +
            "        return service.update(id, input);\n" +
            "    }\n\n" +
            "    @DeleteMapping(\"/{id}\")\n" +
            "    @ResponseStatus(HttpStatus.NO_CONTENT)\n" +
            "    public void deleteById(@PathVariable " + idType + " id) {\n" +
            "        service.deleteById(id);\n" +
            "    }\n" +
            "}\n";
    }

    private String resourceNotFoundException(String pkg) {
        return """
        package %s.error;

        import org.springframework.http.HttpStatus;
        import org.springframework.web.bind.annotation.ResponseStatus;

        @ResponseStatus(HttpStatus.NOT_FOUND)
        public class ResourceNotFoundException extends RuntimeException {
            public ResourceNotFoundException(String message) {
                super(message);
            }
        }
        """.formatted(pkg);
    }

    private String errorResponse(String pkg) {
        return """
        package %s.error;

        import java.time.Instant;
        import java.util.Map;

        public record ErrorResponse(
            int status,
            String error,
            String message,
            String path,
            Instant timestamp,
            Map<String, String> validationErrors
        ) {
            public ErrorResponse(int status, String error, String message, String path) {
                this(status, error, message, path, Instant.now(), null);
            }
            public ErrorResponse(int status, String error, String message, String path, Map<String, String> validationErrors) {
                this(status, error, message, path, Instant.now(), validationErrors);
            }
        }
        """.formatted(pkg);
    }

    private String globalExceptionHandler(String pkg) {
        return """
        package %s.error;

        import jakarta.servlet.http.HttpServletRequest;
        import org.springframework.http.HttpStatus;
        import org.springframework.http.ResponseEntity;
        import org.springframework.security.access.AccessDeniedException;
        import org.springframework.security.authentication.BadCredentialsException;
        import org.springframework.validation.FieldError;
        import org.springframework.web.bind.MethodArgumentNotValidException;
        import org.springframework.web.bind.annotation.ExceptionHandler;
        import org.springframework.web.bind.annotation.RestControllerAdvice;

        import java.util.HashMap;
        import java.util.Map;

        @RestControllerAdvice
        public class GlobalExceptionHandler {

            @ExceptionHandler(ResourceNotFoundException.class)
            public ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException ex, HttpServletRequest request) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErrorResponse(HttpStatus.NOT_FOUND.value(), "Not Found", ex.getMessage(), request.getRequestURI()));
            }

            @ExceptionHandler(MethodArgumentNotValidException.class)
            public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
                Map<String, String> errors = new HashMap<>();
                for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
                    errors.put(fieldError.getField(), fieldError.getDefaultMessage());
                }
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ErrorResponse(HttpStatus.BAD_REQUEST.value(), "Bad Request", "Error de validación", request.getRequestURI(), errors));
            }

            @ExceptionHandler(IllegalArgumentException.class)
            public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest request) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ErrorResponse(HttpStatus.BAD_REQUEST.value(), "Bad Request", ex.getMessage(), request.getRequestURI()));
            }

            @ExceptionHandler(BadCredentialsException.class)
            public ResponseEntity<ErrorResponse> handleBadCredentials(BadCredentialsException ex, HttpServletRequest request) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse(HttpStatus.UNAUTHORIZED.value(), "Unauthorized", ex.getMessage(), request.getRequestURI()));
            }

            @ExceptionHandler(AccessDeniedException.class)
            public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new ErrorResponse(HttpStatus.FORBIDDEN.value(), "Forbidden", ex.getMessage(), request.getRequestURI()));
            }

            @ExceptionHandler(Exception.class)
            public ResponseEntity<ErrorResponse> handleGeneral(Exception ex, HttpServletRequest request) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR.value(), "Internal Server Error", ex.getMessage(), request.getRequestURI()));
            }
        }
        """.formatted(pkg);
    }

    private String authRole(String pkg) {
        return """
        package %s.auth;

        public enum AuthRole {
            ROLE_ADMIN,
            ROLE_USER
        }
        """.formatted(pkg);
    }

    private String authUser(String pkg) {
        return """
        package %s.auth;

        import jakarta.persistence.*;
        import java.time.Instant;
        import java.util.UUID;

        @Entity
        @Table(name = "_app_auth_users")
        public class AuthUser {
            @Id
            private UUID id;

            @Column(nullable = false, unique = true)
            private String email;

            @Column(name = "password_hash", nullable = false)
            private String passwordHash;

            @Column(name = "full_name", nullable = false)
            private String fullName;

            @Enumerated(EnumType.STRING)
            @Column(nullable = false)
            private AuthRole role;

            @Column(name = "created_at", nullable = false)
            private Instant createdAt;

            public AuthUser() {}

            public AuthUser(UUID id, String email, String passwordHash, String fullName, AuthRole role, Instant createdAt) {
                this.id = id;
                this.email = email;
                this.passwordHash = passwordHash;
                this.fullName = fullName;
                this.role = role;
                this.createdAt = createdAt;
            }

            public UUID getId() { return id; }
            public void setId(UUID id) { this.id = id; }
            public String getEmail() { return email; }
            public void setEmail(String email) { this.email = email; }
            public String getPasswordHash() { return passwordHash; }
            public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
            public String getFullName() { return fullName; }
            public void setFullName(String fullName) { this.fullName = fullName; }
            public AuthRole getRole() { return role; }
            public void setRole(AuthRole role) { this.role = role; }
            public Instant getCreatedAt() { return createdAt; }
            public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
        }
        """.formatted(pkg);
    }

    private String authRefreshToken(String pkg) {
        return """
        package %s.auth;

        import jakarta.persistence.*;
        import java.time.Instant;
        import java.util.UUID;

        @Entity
        @Table(name = "_app_auth_tokens")
        public class AuthRefreshToken {
            @Id
            private UUID id;

            @Column(name = "user_id", nullable = false)
            private UUID userId;

            @Column(nullable = false, unique = true, length = 512)
            private String token;

            @Column(name = "expires_at", nullable = false)
            private Instant expiresAt;

            @Column(nullable = false)
            private boolean revoked;

            @Column(name = "created_at", nullable = false)
            private Instant createdAt;

            public AuthRefreshToken() {}

            public AuthRefreshToken(UUID id, UUID userId, String token, Instant expiresAt, boolean revoked, Instant createdAt) {
                this.id = id;
                this.userId = userId;
                this.token = token;
                this.expiresAt = expiresAt;
                this.revoked = revoked;
                this.createdAt = createdAt;
            }

            public UUID getId() { return id; }
            public UUID getUserId() { return userId; }
            public String getToken() { return token; }
            public Instant getExpiresAt() { return expiresAt; }
            public boolean isRevoked() { return revoked; }
            public void setRevoked(boolean revoked) { this.revoked = revoked; }
            public Instant getCreatedAt() { return createdAt; }
        }
        """.formatted(pkg);
    }

    private String authUserRepository(String pkg) {
        return """
        package %s.auth;

        import org.springframework.data.jpa.repository.JpaRepository;
        import java.util.Optional;
        import java.util.UUID;

        public interface AuthUserRepository extends JpaRepository<AuthUser, UUID> {
            Optional<AuthUser> findByEmail(String email);
        }
        """.formatted(pkg);
    }

    private String authTokenRepository(String pkg) {
        return """
        package %s.auth;

        import org.springframework.data.jpa.repository.JpaRepository;
        import java.util.Optional;
        import java.util.UUID;

        public interface AuthTokenRepository extends JpaRepository<AuthRefreshToken, UUID> {
            Optional<AuthRefreshToken> findByTokenAndRevokedFalse(String token);
        }
        """.formatted(pkg);
    }

    private String authDtos(String pkg) {
        return """
        package %s.auth;

        import jakarta.validation.constraints.Email;
        import jakarta.validation.constraints.NotBlank;
        import jakarta.validation.constraints.Size;
        import java.util.UUID;

        public class AuthDtos {
            public record RegisterRequest(
                @NotBlank @Email String email,
                @NotBlank @Size(min = 6) String password,
                @NotBlank String fullName
            ) {}

            public record LoginRequest(
                @NotBlank @Email String email,
                @NotBlank String password
            ) {}

            public record RefreshRequest(
                @NotBlank String refreshToken
            ) {}

            public record UserResponse(
                UUID id,
                String email,
                String fullName,
                AuthRole role
            ) {}

            public record TokenResponse(
                String accessToken,
                String refreshToken,
                String tokenType,
                UserResponse user
            ) {}
        }
        """.formatted(pkg);
    }

    private String jwtService(String pkg) {
        return """
        package %s.auth;

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
        public class JwtService {
            private final String secret;
            private final long accessExpirationMs;
            private final long refreshExpirationMs;
            private final ObjectMapper mapper = new ObjectMapper();

            public JwtService(
                @Value("${app.jwt.secret:very-secret-jwt-key-for-development-purposes-only-32bytes}") String secret,
                @Value("${app.jwt.access-expiration-ms:3600000}") long accessExpirationMs,
                @Value("${app.jwt.refresh-expiration-ms:604800000}") long refreshExpirationMs
            ) {
                this.secret = secret;
                this.accessExpirationMs = accessExpirationMs;
                this.refreshExpirationMs = refreshExpirationMs;
            }

            public String generateAccessToken(AuthUser user) {
                return buildToken(user.getId().toString(), user.getEmail(), user.getRole().name(), user.getFullName(), accessExpirationMs);
            }

            public String generateRefreshToken(AuthUser user) {
                return buildToken(user.getId().toString(), user.getEmail(), user.getRole().name(), user.getFullName(), refreshExpirationMs);
            }

            public boolean validateToken(String token) {
                try {
                    String[] parts = token.split("\\\\.");
                    if (parts.length != 3) return false;
                    String expectedSig = hmacSha256(parts[0] + "." + parts[1], secret);
                    if (!MessageDigest.isEqual(parts[2].getBytes(StandardCharsets.UTF_8), expectedSig.getBytes(StandardCharsets.UTF_8))) {
                        return false;
                    }
                    String payloadJson = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
                    Map<?, ?> claims = mapper.readValue(payloadJson, Map.class);
                    long exp = ((Number) claims.get("exp")).longValue();
                    return Instant.now().getEpochSecond() < exp;
                } catch (Exception ex) {
                    return false;
                }
            }

            public Map<String, Object> parseClaims(String token) {
                try {
                    String[] parts = token.split("\\\\.");
                    String payloadJson = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
                    return mapper.readValue(payloadJson, Map.class);
                } catch (Exception ex) {
                    throw new IllegalArgumentException("Token inválido", ex);
                }
            }

            public String extractEmail(String token) {
                return (String) parseClaims(token).get("email");
            }

            public String extractRole(String token) {
                return (String) parseClaims(token).get("role");
            }

            public String extractSubject(String token) {
                return (String) parseClaims(token).get("sub");
            }

            public long getRefreshExpirationMs() {
                return refreshExpirationMs;
            }

            private String buildToken(String sub, String email, String role, String fullName, long ttlMs) {
                try {
                    String headerJson = "{\\"alg\\":\\"HS256\\",\\"typ\\":\\"JWT\\"}";
                    long iat = Instant.now().getEpochSecond();
                    long exp = Instant.now().plusMillis(ttlMs).getEpochSecond();

                    Map<String, Object> payload = new LinkedHashMap<>();
                    payload.put("sub", sub);
                    payload.put("email", email);
                    payload.put("role", role);
                    payload.put("fullName", fullName);
                    payload.put("iat", iat);
                    payload.put("exp", exp);

                    String encodedHeader = Base64.getUrlEncoder().withoutPadding().encodeToString(headerJson.getBytes(StandardCharsets.UTF_8));
                    String encodedPayload = Base64.getUrlEncoder().withoutPadding().encodeToString(mapper.writeValueAsBytes(payload));
                    String data = encodedHeader + "." + encodedPayload;
                    String signature = hmacSha256(data, secret);
                    return data + "." + signature;
                } catch (Exception ex) {
                    throw new IllegalStateException("Error al generar token JWT", ex);
                }
            }

            private String hmacSha256(String data, String key) throws Exception {
                Mac mac = Mac.getInstance("HmacSHA256");
                mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
                return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
            }
        }
        """.formatted(pkg);
    }

    private String authService(String pkg) {
        return """
        package %s.auth;

        import org.springframework.security.authentication.BadCredentialsException;
        import org.springframework.security.crypto.password.PasswordEncoder;
        import org.springframework.stereotype.Service;
        import org.springframework.transaction.annotation.Transactional;

        import java.time.Instant;
        import java.util.UUID;

        @Service
        @Transactional
        public class AuthService {
            private final AuthUserRepository users;
            private final AuthTokenRepository tokens;
            private final JwtService jwt;
            private final PasswordEncoder passwordEncoder;

            public AuthService(
                AuthUserRepository users,
                AuthTokenRepository tokens,
                JwtService jwt,
                PasswordEncoder passwordEncoder
            ) {
                this.users = users;
                this.tokens = tokens;
                this.jwt = jwt;
                this.passwordEncoder = passwordEncoder;
            }

            public AuthDtos.TokenResponse register(AuthDtos.RegisterRequest request) {
                if (users.findByEmail(request.email().toLowerCase()).isPresent()) {
                    throw new IllegalArgumentException("El correo ya se encuentra registrado: " + request.email());
                }
                // The first registered user gets ROLE_ADMIN, subsequent users get ROLE_USER
                AuthRole role = users.count() == 0 ? AuthRole.ROLE_ADMIN : AuthRole.ROLE_USER;
                AuthUser user = new AuthUser(
                    UUID.randomUUID(),
                    request.email().toLowerCase().trim(),
                    passwordEncoder.encode(request.password()),
                    request.fullName().trim(),
                    role,
                    Instant.now()
                );
                users.save(user);
                return createTokenResponse(user);
            }

            public AuthDtos.TokenResponse login(AuthDtos.LoginRequest request) {
                AuthUser user = users.findByEmail(request.email().toLowerCase().trim())
                    .orElseThrow(() -> new BadCredentialsException("Credenciales inválidas"));
                if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
                    throw new BadCredentialsException("Credenciales inválidas");
                }
                return createTokenResponse(user);
            }

            public AuthDtos.TokenResponse refresh(AuthDtos.RefreshRequest request) {
                if (!jwt.validateToken(request.refreshToken())) {
                    throw new BadCredentialsException("Token de refresco inválido o expirado");
                }
                AuthRefreshToken savedToken = tokens.findByTokenAndRevokedFalse(request.refreshToken())
                    .orElseThrow(() -> new BadCredentialsException("Token de refresco revocado o no encontrado"));
                if (Instant.now().isAfter(savedToken.getExpiresAt())) {
                    savedToken.setRevoked(true);
                    tokens.save(savedToken);
                    throw new BadCredentialsException("Token de refresco expirado");
                }
                AuthUser user = users.findById(savedToken.getUserId())
                    .orElseThrow(() -> new BadCredentialsException("Usuario no encontrado"));

                String newAccess = jwt.generateAccessToken(user);
                return new AuthDtos.TokenResponse(
                    newAccess,
                    request.refreshToken(),
                    "Bearer",
                    new AuthDtos.UserResponse(user.getId(), user.getEmail(), user.getFullName(), user.getRole())
                );
            }

            public AuthDtos.UserResponse me(String email) {
                AuthUser user = users.findByEmail(email.toLowerCase())
                    .orElseThrow(() -> new BadCredentialsException("Usuario no encontrado"));
                return new AuthDtos.UserResponse(user.getId(), user.getEmail(), user.getFullName(), user.getRole());
            }

            private AuthDtos.TokenResponse createTokenResponse(AuthUser user) {
                String accessToken = jwt.generateAccessToken(user);
                String refreshToken = jwt.generateRefreshToken(user);

                AuthRefreshToken tokenEntity = new AuthRefreshToken(
                    UUID.randomUUID(),
                    user.getId(),
                    refreshToken,
                    Instant.now().plusMillis(jwt.getRefreshExpirationMs()),
                    false,
                    Instant.now()
                );
                tokens.save(tokenEntity);

                return new AuthDtos.TokenResponse(
                    accessToken,
                    refreshToken,
                    "Bearer",
                    new AuthDtos.UserResponse(user.getId(), user.getEmail(), user.getFullName(), user.getRole())
                );
            }
        }
        """.formatted(pkg);
    }

    private String jwtAuthenticationFilter(String pkg) {
        return """
        package %s.auth;

        import jakarta.servlet.FilterChain;
        import jakarta.servlet.ServletException;
        import jakarta.servlet.http.HttpServletRequest;
        import jakarta.servlet.http.HttpServletResponse;
        import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
        import org.springframework.security.core.authority.SimpleGrantedAuthority;
        import org.springframework.security.core.context.SecurityContextHolder;
        import org.springframework.stereotype.Component;
        import org.springframework.web.filter.OncePerRequestFilter;

        import java.io.IOException;
        import java.util.List;

        @Component
        public class JwtAuthenticationFilter extends OncePerRequestFilter {
            private final JwtService jwtService;

            public JwtAuthenticationFilter(JwtService jwtService) {
                this.jwtService = jwtService;
            }

            @Override
            protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
                    throws ServletException, IOException {
                String authHeader = request.getHeader("Authorization");
                if (authHeader != null && authHeader.startsWith("Bearer ")) {
                    String token = authHeader.substring(7);
                    if (jwtService.validateToken(token)) {
                        String email = jwtService.extractEmail(token);
                        String role = jwtService.extractRole(token);
                        var auth = new UsernamePasswordAuthenticationToken(
                            email, null, List.of(new SimpleGrantedAuthority(role))
                        );
                        SecurityContextHolder.getContext().setAuthentication(auth);
                    }
                }
                filterChain.doFilter(request, response);
            }
        }
        """.formatted(pkg);
    }

    private String securityConfig(String pkg) {
        return """
        package %s.auth;

        import org.springframework.context.annotation.Bean;
        import org.springframework.context.annotation.Configuration;
        import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
        import org.springframework.security.config.annotation.web.builders.HttpSecurity;
        import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
        import org.springframework.security.config.http.SessionCreationPolicy;
        import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
        import org.springframework.security.crypto.password.PasswordEncoder;
        import org.springframework.security.web.SecurityFilterChain;
        import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

        @Configuration
        @EnableWebSecurity
        @EnableMethodSecurity
        public class SecurityConfig {
            private final JwtAuthenticationFilter jwtFilter;

            public SecurityConfig(JwtAuthenticationFilter jwtFilter) {
                this.jwtFilter = jwtFilter;
            }

            @Bean
            public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
                return http
                    .csrf(csrf -> csrf.disable())
                    .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                    .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                            "/api/auth/**",
                            "/v3/api-docs/**",
                            "/swagger-ui/**",
                            "/swagger-ui.html",
                            "/actuator/health",
                            "/openapi.yaml"
                        ).permitAll()
                        .anyRequest().authenticated()
                    )
                    .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                    .build();
            }

            @Bean
            public PasswordEncoder passwordEncoder() {
                return new BCryptPasswordEncoder();
            }
        }
        """.formatted(pkg);
    }

    private String authController(String pkg) {
        return """
        package %s.auth;

        import jakarta.validation.Valid;
        import org.springframework.http.ResponseEntity;
        import org.springframework.security.core.annotation.AuthenticationPrincipal;
        import org.springframework.web.bind.annotation.*;

        @RestController
        @RequestMapping("/api/auth")
        public class AuthController {
            private final AuthService authService;

            public AuthController(AuthService authService) {
                this.authService = authService;
            }

            @PostMapping("/register")
            public ResponseEntity<AuthDtos.TokenResponse> register(@Valid @RequestBody AuthDtos.RegisterRequest request) {
                return ResponseEntity.ok(authService.register(request));
            }

            @PostMapping("/login")
            public ResponseEntity<AuthDtos.TokenResponse> login(@Valid @RequestBody AuthDtos.LoginRequest request) {
                return ResponseEntity.ok(authService.login(request));
            }

            @PostMapping("/refresh")
            public ResponseEntity<AuthDtos.TokenResponse> refresh(@Valid @RequestBody AuthDtos.RefreshRequest request) {
                return ResponseEntity.ok(authService.refresh(request));
            }

            @GetMapping("/me")
            public ResponseEntity<AuthDtos.UserResponse> me(@AuthenticationPrincipal String email) {
                return ResponseEntity.ok(authService.me(email));
            }
        }
        """.formatted(pkg);
    }

    private String migration(DiagramDocument diagram) {
        StringBuilder sql = new StringBuilder();

        // 1. Isolated Auth Tables
        sql.append("""
        -- Isolated Authentication Schema
        create table _app_auth_users (
            id uuid primary key,
            email varchar(255) not null unique,
            password_hash varchar(255) not null,
            full_name varchar(255) not null,
            role varchar(50) not null,
            created_at timestamp with time zone not null
        );

        create table _app_auth_tokens (
            id uuid primary key,
            user_id uuid not null references _app_auth_users(id) on delete cascade,
            token varchar(512) not null unique,
            expires_at timestamp with time zone not null,
            revoked boolean not null default false,
            created_at timestamp with time zone not null
        );

        create index idx_auth_users_email on _app_auth_users(email);
        create index idx_auth_tokens_token on _app_auth_tokens(token);

        """);

        // 2. Domain Entities - Parent classes first (for JOINED inheritance)
        List<DiagramDocument.ClassElement> sortedClasses = new ArrayList<>();
        Set<UUID> added = new HashSet<>();
        // Parents first
        for (var item : diagram.classes()) {
            if (findParentId(diagram, item.id()) == null) {
                sortedClasses.add(item);
                added.add(item.id());
            }
        }
        // Children next
        for (var item : diagram.classes()) {
            if (!added.contains(item.id())) {
                sortedClasses.add(item);
                added.add(item.id());
            }
        }

        List<String> alterForeignKeys = new ArrayList<>();

        for (var item : sortedClasses) {
            String tableName = sqlName(item.name());
            UUID parentId = findParentId(diagram, item.id());
            boolean hasParent = parentId != null;

            sql.append("create table ").append(tableName).append(" (\n");
            List<String> cols = new ArrayList<>();
            List<String> primaryColumns = item.attributes().stream().filter(DiagramDocument.Attribute::primaryKey)
                .map(a -> sqlName(a.name())).toList();

            if (hasParent) {
                var parent = findClass(diagram, parentId);
                String parentPkCol = sqlName(getPkFieldName(diagram, parent));
                String parentTable = sqlName(parent.name());
                cols.add("    " + parentPkCol + " uuid primary key references " + parentTable + "(" + parentPkCol + ") on delete cascade");
            }

            for (var a : item.attributes()) {
                if (hasParent && a.primaryKey()) continue;
                StringBuilder col = new StringBuilder("    ").append(sqlName(a.name())).append(" ").append(sqlType(a.type()));
                if (a.primaryKey() && primaryColumns.size() == 1) col.append(" primary key");
                if (a.required()) col.append(" not null");
                if (a.unique()) col.append(" unique");
                cols.add(col.toString());
            }
            if (!hasParent && primaryColumns.size() > 1) cols.add("    primary key (" + String.join(", ", primaryColumns) + ")");

            // Foreign keys on the table
            for (var assoc : diagram.associations()) {
                if (assoc.sourceId().equals(item.id())) {
                    var target = findClass(diagram, assoc.targetId());
                    String targetPkType = sqlType(idType(diagram, target));
                    String targetTable = sqlName(target.name());
                    String targetPkCol = sqlName(getPkFieldName(diagram, target));
                    String colName = sqlName(target.name()) + "_id";

                    if (isManyToOne(assoc.sourceCardinality(), assoc.targetCardinality())) {
                        if (item.attributes().stream().noneMatch(a -> sqlName(a.name()).equals(colName))) cols.add("    " + colName + " " + targetPkType);
                        alterForeignKeys.add("alter table " + tableName + " add constraint fk_" + tableName + "_" + colName + " foreign key (" + colName + ") references " + targetTable + "(" + targetPkCol + ");");
                    } else if (isOneToOne(assoc.sourceCardinality(), assoc.targetCardinality()) && "SOURCE".equalsIgnoreCase(assoc.owningSide())) {
                        cols.add("    " + colName + " " + targetPkType + " unique");
                        alterForeignKeys.add("alter table " + tableName + " add constraint fk_" + tableName + "_" + colName + " foreign key (" + colName + ") references " + targetTable + "(" + targetPkCol + ");");
                    }
                } else if (assoc.targetId().equals(item.id())) {
                    var source = findClass(diagram, assoc.sourceId());
                    String sourcePkType = sqlType(idType(diagram, source));
                    String sourceTable = sqlName(source.name());
                    String sourcePkCol = sqlName(getPkFieldName(diagram, source));
                    String colName = sqlName(source.name()) + "_id";

                    if (isOneToMany(assoc.sourceCardinality(), assoc.targetCardinality())) {
                        if (item.attributes().stream().noneMatch(a -> sqlName(a.name()).equals(colName))) cols.add("    " + colName + " " + sourcePkType);
                        alterForeignKeys.add("alter table " + tableName + " add constraint fk_" + tableName + "_" + colName + " foreign key (" + colName + ") references " + sourceTable + "(" + sourcePkCol + ");");
                    } else if (isOneToOne(assoc.sourceCardinality(), assoc.targetCardinality()) && "TARGET".equalsIgnoreCase(assoc.owningSide())) {
                        cols.add("    " + colName + " " + sourcePkType + " unique");
                        alterForeignKeys.add("alter table " + tableName + " add constraint fk_" + tableName + "_" + colName + " foreign key (" + colName + ") references " + sourceTable + "(" + sourcePkCol + ");");
                    }
                }
            }

            sql.append(String.join(",\n", cols)).append("\n);\n\n");
        }

        // 3. Join tables for Many-to-Many associations
        for (var assoc : diagram.associations()) {
            if (isManyToMany(assoc.sourceCardinality(), assoc.targetCardinality())) {
                var src = findClass(diagram, assoc.sourceId());
                var tgt = findClass(diagram, assoc.targetId());
                String joinTable = sqlName(src.name()) + "_" + sqlName(tgt.name());
                String srcCol = sqlName(src.name()) + "_id";
                String tgtCol = sqlName(tgt.name()) + "_id";
                String srcType = sqlType(idType(diagram, src));
                String tgtType = sqlType(idType(diagram, tgt));
                String srcPkCol = sqlName(getPkFieldName(diagram, src));
                String tgtPkCol = sqlName(getPkFieldName(diagram, tgt));

                sql.append("create table ").append(joinTable).append(" (\n");
                sql.append("    ").append(srcCol).append(" ").append(srcType).append(" not null references ").append(sqlName(src.name())).append("(").append(srcPkCol).append(") on delete cascade,\n");
                sql.append("    ").append(tgtCol).append(" ").append(tgtType).append(" not null references ").append(sqlName(tgt.name())).append("(").append(tgtPkCol).append(") on delete cascade,\n");
                sql.append("    primary key (").append(srcCol).append(", ").append(tgtCol).append(")\n");
                sql.append(");\n\n");
            }
        }

        // 4. Foreign Key Constraints
        if (!alterForeignKeys.isEmpty()) {
            sql.append("-- Foreign Key Constraints\n");
            for (String fk : alterForeignKeys) {
                sql.append(fk).append("\n");
            }
            sql.append("\n");
        }

        return sql.toString();
    }

    private String applicationTests(String pkg) {
        return """
        package %s;

        import %s.auth.*;
        import org.junit.jupiter.api.Test;
        import org.springframework.beans.factory.annotation.Autowired;
        import org.springframework.boot.test.context.SpringBootTest;
        import org.springframework.test.context.ActiveProfiles;

        import static org.junit.jupiter.api.Assertions.*;

        @SpringBootTest
        @ActiveProfiles("test")
        class ApplicationTests {

            @Autowired(required = false)
            private AuthService authService;

            @Autowired(required = false)
            private AuthUserRepository authUserRepository;

            @Test
            void contextLoads() {
                assertNotNull(authService, "AuthService debe estar inicializado en el contexto");
            }

            @Test
            void firstRegisteredUserIsAdmin() {
                if (authService == null || authUserRepository == null) return;
                authUserRepository.deleteAll();
                var first = authService.register(new AuthDtos.RegisterRequest("first_admin@example.com", "pass12345", "Primer Admin"));
                assertEquals(AuthRole.ROLE_ADMIN, first.user().role(), "El primer usuario debe ser ADMIN");

                var second = authService.register(new AuthDtos.RegisterRequest("second_user@example.com", "pass12345", "Segundo Usuario"));
                assertEquals(AuthRole.ROLE_USER, second.user().role(), "El segundo usuario debe ser USER");
            }
        }
        """.formatted(pkg, pkg);
    }

    // Helper utilities
    private boolean isExplicitJoinClass(DiagramDocument diagram, DiagramDocument.ClassElement item) {
        var keys = item.attributes().stream().filter(DiagramDocument.Attribute::primaryKey).toList();
        if (keys.size() != 2) return false;
        var parents = diagram.associations().stream()
            .filter(link -> link.targetId().equals(item.id())
                && isOneToMany(link.sourceCardinality(), link.targetCardinality()))
            .map(link -> findClass(diagram, link.sourceId())).distinct().toList();
        return parents.size() == 2 && parents.stream().allMatch(parent -> keys.stream()
            .anyMatch(key -> key.name().equalsIgnoreCase(parent.name() + "_id")));
    }

    private String compositeId(String pkg, DiagramDocument diagram, DiagramDocument.ClassElement item) {
        String name = javaName(item.name(), true) + "Id";
        var keys = item.attributes().stream().filter(DiagramDocument.Attribute::primaryKey).toList();
        StringBuilder sb = new StringBuilder("package " + pkg + ".model;\n\n" +
            "import java.io.Serializable;\nimport java.util.*;\nimport java.math.BigDecimal;\nimport java.time.*;\n\n" +
            "public class " + name + " implements Serializable {\n");
        for (var key : keys) sb.append("    public ").append(javaType(diagram, key.type())).append(" ")
            .append(javaName(key.name(), false)).append(";\n");
        sb.append("    public ").append(name).append("() {}\n");
        sb.append("    public static ").append(name).append(" valueOf(String raw) {\n")
            .append("        String[] parts = raw.split(\",\", -1);\n")
            .append("        if (parts.length != ").append(keys.size()).append(") throw new IllegalArgumentException(\"Clave compuesta inválida\");\n")
            .append("        ").append(name).append(" result = new ").append(name).append("();\n");
        for (int i = 0; i < keys.size(); i++) {
            var key = keys.get(i);
            String type = javaType(diagram, key.type());
            String raw = "parts[" + i + "].trim()";
            String parsed = switch (type) {
                case "UUID" -> "UUID.fromString(" + raw + ")";
                case "Integer" -> "Integer.valueOf(" + raw + ")";
                case "Long" -> "Long.valueOf(" + raw + ")";
                case "BigDecimal" -> "new BigDecimal(" + raw + ")";
                default -> raw;
            };
            sb.append("        result.").append(javaName(key.name(), false)).append(" = ").append(parsed).append(";\n");
        }
        sb.append("        return result;\n    }\n")
            .append("    @Override public boolean equals(Object other) {\n")
            .append("        if (!(other instanceof ").append(name).append(" that)) return false;\n")
            .append("        return ");
        for (int i = 0; i < keys.size(); i++) {
            if (i > 0) sb.append(" && ");
            String field = javaName(keys.get(i).name(), false);
            sb.append("Objects.equals(").append(field).append(", that.").append(field).append(")");
        }
        sb.append(";\n    }\n    @Override public int hashCode() { return Objects.hash(");
        for (int i = 0; i < keys.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(javaName(keys.get(i).name(), false));
        }
        sb.append("); }\n    @Override public String toString() { return ");
        for (int i = 0; i < keys.size(); i++) {
            if (i > 0) sb.append(" + \",\" + ");
            sb.append("String.valueOf(").append(javaName(keys.get(i).name(), false)).append(")");
        }
        sb.append("; }\n}\n");
        return sb.toString();
    }

    private boolean isEnum(DiagramDocument diagram, String type) {
        return diagram.enumerations().stream().anyMatch(e -> e.name().equalsIgnoreCase(type));
    }

    private String idType(DiagramDocument diagram, DiagramDocument.ClassElement item) {
        if (isExplicitJoinClass(diagram, item)) return javaName(item.name(), true) + "Id";
        var pk = item.attributes().stream().filter(DiagramDocument.Attribute::primaryKey).findFirst();
        if (pk.isPresent()) return javaType(diagram, pk.get().type());
        UUID parentId = findParentId(diagram, item.id());
        if (parentId != null) {
            return idType(diagram, findClass(diagram, parentId));
        }
        return "UUID";
    }

    private String getPkFieldName(DiagramDocument diagram, DiagramDocument.ClassElement item) {
        var pk = item.attributes().stream().filter(DiagramDocument.Attribute::primaryKey).findFirst();
        if (pk.isPresent()) return pk.get().name();
        UUID parentId = findParentId(diagram, item.id());
        if (parentId != null) {
            return getPkFieldName(diagram, findClass(diagram, parentId));
        }
        return "id";
    }

    private UUID findParentId(DiagramDocument diagram, UUID childId) {
        for (var gen : diagram.generalizations()) {
            if (gen.childId().equals(childId)) return gen.parentId();
        }
        return null;
    }

    private boolean isParentClass(DiagramDocument diagram, UUID classId) {
        return diagram.generalizations().stream().anyMatch(g -> g.parentId().equals(classId));
    }

    private DiagramDocument.ClassElement findClass(DiagramDocument diagram, UUID id) {
        return diagram.classes().stream().filter(c -> c.id().equals(id)).findFirst().orElseThrow();
    }

    private boolean isManyToOne(String sourceCard, String targetCard) {
        return (sourceCard.contains("*") || sourceCard.contains("N")) && (targetCard.equals("1") || targetCard.equals("0..1"));
    }

    private boolean isOneToMany(String sourceCard, String targetCard) {
        return (sourceCard.equals("1") || sourceCard.equals("0..1")) && (targetCard.contains("*") || targetCard.contains("N"));
    }

    private boolean isOneToOne(String sourceCard, String targetCard) {
        return (sourceCard.equals("1") || sourceCard.equals("0..1")) && (targetCard.equals("1") || targetCard.equals("0..1"));
    }

    private boolean isManyToMany(String sourceCard, String targetCard) {
        return (sourceCard.contains("*") || sourceCard.contains("N")) && (targetCard.contains("*") || targetCard.contains("N"));
    }

    private String javaType(DiagramDocument diagram, String type) {
        if (isEnum(diagram, type)) {
            var en = diagram.enumerations().stream().filter(e -> e.name().equalsIgnoreCase(type)).findFirst().orElseThrow();
            return javaName(en.name(), true);
        }
        return switch (type) {
            case "Integer" -> "Integer";
            case "Long" -> "Long";
            case "Decimal" -> "BigDecimal";
            case "Boolean" -> "Boolean";
            case "Date" -> "LocalDate";
            case "DateTime" -> "LocalDateTime";
            case "UUID" -> "UUID";
            case "Binary" -> "byte[]";
            case "Text" -> "String";
            default -> "String";
        };
    }

    private String sqlType(String type) {
        return switch (type) {
            case "Integer" -> "integer";
            case "Long" -> "bigint";
            case "Decimal" -> "numeric(19,2)";
            case "Boolean" -> "boolean";
            case "Date" -> "date";
            case "DateTime" -> "timestamp with time zone";
            case "UUID" -> "uuid";
            case "Binary" -> "bytea";
            case "Text" -> "text";
            default -> "varchar(255)";
        };
    }

    private String javaName(String value, boolean capitalize) {
        String clean = value.replaceAll("[^A-Za-z0-9_]", "_");
        return capitalize ? Character.toUpperCase(clean.charAt(0)) + clean.substring(1) : Character.toLowerCase(clean.charAt(0)) + clean.substring(1);
    }

    private String sqlName(String value) {
        return value.replaceAll("([a-z0-9])([A-Z])", "$1_$2").replaceAll("[^A-Za-z0-9]", "_").toLowerCase(Locale.ROOT);
    }

    private String plural(String name) {
        return name.endsWith("s") ? name + "es" : name + "s";
    }

    private int lineOf(String content, String needle) {
        if (content == null) return 1;
        int index = content.indexOf(needle);
        return index < 0 ? 1 : content.substring(0, index).split("\\R", -1).length;
    }

    private byte[] zip(Map<String, String> files) {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            try (ZipOutputStream zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
                for (var file : files.entrySet()) {
                    String entryName = file.getKey();
                    // Protect against path traversal
                    if (entryName.contains("..") || entryName.startsWith("/") || entryName.startsWith("\\")) {
                        throw new SecurityException("Ruta no permitida (path traversal detectado): " + entryName);
                    }
                    zip.putNextEntry(new ZipEntry(entryName));
                    zip.write(file.getValue().getBytes(StandardCharsets.UTF_8));
                    zip.closeEntry();
                }
            }
            return output.toByteArray();
        } catch (Exception exception) {
            throw new IllegalStateException("No se pudo generar el ZIP", exception);
        }
    }
}
