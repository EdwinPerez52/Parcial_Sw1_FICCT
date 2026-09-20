package com.collabmodeler.api.generation;

import com.collabmodeler.api.diagram.DiagramDocument;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class GeneratedBackendPostgresIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private BackendGenerator generator;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        generator = new BackendGenerator(mapper, new ModelValidator(), new OpenApiGenerator());
    }

    @Test
    void ventasBackendMigratesAuthenticatesAndExecutesCrudAgainstPostgres() throws Exception {
        var ventasModel = buildVentasModel();
        byte[] zip = generator.generate(ventasModel, "com.example", "ventas-api");
        Map<String, String> files = extractZip(zip);

        // 1. Verify OpenAPI publication
        String openapi = files.get("openapi.yaml");
        assertNotNull(openapi);
        assertTrue(openapi.contains("/api/cliente:"));
        assertTrue(openapi.contains("/api/pedido:"));
        assertTrue(openapi.contains("/api/factura:"));
        assertTrue(openapi.contains("/api/producto:"));
        assertTrue(openapi.contains("/api/auth/register:"));

        // 2. Execute migration against PostgreSQL
        String migrationSql = files.get("src/main/resources/db/migration/V1__initial_schema.sql");
        assertNotNull(migrationSql);
        executeMigrationSql(migrationSql);

        // 3. Test Authentication against isolated tables in PostgreSQL
        var encoder = new BCryptPasswordEncoder();
        UUID adminId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        // First user gets ROLE_ADMIN
        jdbcTemplate.update(
            "insert into _app_auth_users (id, email, password_hash, full_name, role, created_at) values (?, ?, ?, ?, ?, ?)",
            adminId, "admin@ventas.com", encoder.encode("adminPass123"), "Admin Ventas", "ROLE_ADMIN", java.sql.Timestamp.from(Instant.now())
        );

        // Second user gets ROLE_USER
        jdbcTemplate.update(
            "insert into _app_auth_users (id, email, password_hash, full_name, role, created_at) values (?, ?, ?, ?, ?, ?)",
            userId, "user@ventas.com", encoder.encode("userPass123"), "User Ventas", "ROLE_USER", java.sql.Timestamp.from(Instant.now())
        );

        String adminRole = jdbcTemplate.queryForObject("select role from _app_auth_users where id = ?", String.class, adminId);
        String userRole = jdbcTemplate.queryForObject("select role from _app_auth_users where id = ?", String.class, userId);
        assertEquals("ROLE_ADMIN", adminRole, "El primer usuario registrado debe tener ROLE_ADMIN");
        assertEquals("ROLE_USER", userRole, "El segundo usuario registrado debe tener ROLE_USER");

        // Test refresh token persistence in isolated table
        UUID tokenId = UUID.randomUUID();
        jdbcTemplate.update(
            "insert into _app_auth_tokens (id, user_id, token, expires_at, revoked, created_at) values (?, ?, ?, ?, ?, ?)",
            tokenId, adminId, "sample_refresh_token_value", java.sql.Timestamp.from(Instant.now().plusSeconds(3600)), false, java.sql.Timestamp.from(Instant.now())
        );
        assertEquals(1, jdbcTemplate.queryForObject("select count(*) from _app_auth_tokens where user_id = ?", Integer.class, adminId));

        // 4. Test CRUD for domain entities: Cliente, Pedido, Factura, Producto
        UUID clienteId = UUID.randomUUID();
        jdbcTemplate.update("insert into cliente (id, nombre, email) values (?, ?, ?)", clienteId, "Juan Perez", "juan@example.com");

        UUID pedidoId = UUID.randomUUID();
        jdbcTemplate.update(
            "insert into pedido (id, numero, total, estado, cliente_id) values (?, ?, ?, ?, ?)",
            pedidoId, "PED-001", new BigDecimal("199.99"), "NUEVO", clienteId
        );

        UUID facturaId = UUID.randomUUID();
        jdbcTemplate.update(
            "insert into factura (id, numero_factura, monto, pedido_id) values (?, ?, ?, ?)",
            facturaId, "FAC-001", new BigDecimal("199.99"), pedidoId
        );

        UUID productoId = UUID.randomUUID();
        jdbcTemplate.update("insert into producto (id, codigo, precio, stock) values (?, ?, ?, ?)", productoId, "PROD-1", new BigDecimal("49.99"), 100);

        // N:M relation in join table
        jdbcTemplate.update("insert into pedido_producto (pedido_id, producto_id) values (?, ?)", pedidoId, productoId);

        // Query with joins to verify relational integrity
        var count = jdbcTemplate.queryForObject(
            "select count(*) from pedido p " +
            "join cliente c on p.cliente_id = c.id " +
            "join factura f on f.pedido_id = p.id " +
            "join pedido_producto pp on pp.pedido_id = p.id " +
            "where p.id = ?",
            Integer.class, pedidoId
        );
        assertEquals(1, count, "El grafo relacional de Ventas debe persistirse y consultarse íntegramente");

        // Update & Delete CRUD operations
        jdbcTemplate.update("update pedido set estado = ? where id = ?", "PAGADO", pedidoId);
        String updatedEstado = jdbcTemplate.queryForObject("select estado from pedido where id = ?", String.class, pedidoId);
        assertEquals("PAGADO", updatedEstado);

        jdbcTemplate.update("delete from pedido_producto where pedido_id = ?", pedidoId);
        jdbcTemplate.update("delete from factura where id = ?", facturaId);
        jdbcTemplate.update("delete from pedido where id = ?", pedidoId);
        jdbcTemplate.update("delete from cliente where id = ?", clienteId);
        jdbcTemplate.update("delete from producto where id = ?", productoId);
    }

    @Test
    void colegioBackendMigratesAndExecutesJoinedInheritanceCrudAgainstPostgres() throws Exception {
        var colegioModel = buildColegioModel();
        byte[] zip = generator.generate(colegioModel, "com.example", "colegio-api");
        Map<String, String> files = extractZip(zip);

        String openapi = files.get("openapi.yaml");
        assertNotNull(openapi);
        assertTrue(openapi.contains("/api/estudiante:"));
        assertTrue(openapi.contains("/api/profesor:"));
        assertTrue(openapi.contains("/api/curso:"));

        String migrationSql = files.get("src/main/resources/db/migration/V1__initial_schema.sql");
        executeMigrationSql(migrationSql);

        // Test JOINED Inheritance: Persona -> Estudiante, Persona -> Profesor
        UUID estId = UUID.randomUUID();
        jdbcTemplate.update("insert into persona (id, nombre, dni) values (?, ?, ?)", estId, "Carlos Alumno", "12345678");
        jdbcTemplate.update("insert into estudiante (id, codigo_estudiante, nivel) values (?, ?, ?)", estId, "EST-2026", "SECUNDARIA");

        UUID profId = UUID.randomUUID();
        jdbcTemplate.update("insert into persona (id, nombre, dni) values (?, ?, ?)", profId, "Maria Docente", "87654321");
        jdbcTemplate.update("insert into profesor (id, especialidad, salario) values (?, ?, ?)", profId, "Matematicas", new BigDecimal("3500.00"));

        // Query Joined Inheritance
        var estRow = jdbcTemplate.queryForMap(
            "select p.nombre, p.dni, e.codigo_estudiante, e.nivel from estudiante e join persona p on e.id = p.id where e.id = ?", estId
        );
        assertEquals("Carlos Alumno", estRow.get("nombre"));
        assertEquals("EST-2026", estRow.get("codigo_estudiante"));
        assertEquals("SECUNDARIA", estRow.get("nivel"));

        // Test Curso and Aula (1:1), 1:N and N:M
        UUID aulaId = UUID.randomUUID();
        jdbcTemplate.update("insert into aula (id, numero_aula, capacidad) values (?, ?, ?)", aulaId, "A-101", 35);

        UUID cursoId = UUID.randomUUID();
        jdbcTemplate.update(
            "insert into curso (id, nombre_curso, creditos, profesor_id, aula_id) values (?, ?, ?, ?, ?)",
            cursoId, "Algoritmos", 4, profId, aulaId
        );

        jdbcTemplate.update("insert into estudiante_curso (estudiante_id, curso_id) values (?, ?)", estId, cursoId);

        var cursoCount = jdbcTemplate.queryForObject(
            "select count(*) from curso c " +
            "join profesor pr on c.profesor_id = pr.id " +
            "join aula a on c.aula_id = a.id " +
            "join estudiante_curso ec on ec.curso_id = c.id " +
            "where c.id = ?",
            Integer.class, cursoId
        );
        assertEquals(1, cursoCount, "El modelo Colegio con herencia JOINED y relaciones debe consultarse correctamente");
    }

    @Test
    void saludBackendMigratesAndExecutesCrudAgainstPostgres() throws Exception {
        var saludModel = buildSaludModel();
        byte[] zip = generator.generate(saludModel, "com.example", "salud-api");
        Map<String, String> files = extractZip(zip);

        String migrationSql = files.get("src/main/resources/db/migration/V1__initial_schema.sql");
        executeMigrationSql(migrationSql);

        UUID pacId = UUID.randomUUID();
        jdbcTemplate.update("insert into paciente (id, nombre, fecha_nacimiento) values (?, ?, ?)", pacId, "Paciente Uno", LocalDate.of(1990, 5, 20));

        UUID histId = UUID.randomUUID();
        jdbcTemplate.update("insert into historia_clinica (id, numero_historial, paciente_id) values (?, ?, ?)", histId, "HC-001", pacId);

        UUID medId = UUID.randomUUID();
        jdbcTemplate.update("insert into medico (id, nombre, colegiatura) values (?, ?, ?)", medId, "Dr. Perez", "CMP-9988");

        UUID citaId = UUID.randomUUID();
        jdbcTemplate.update("insert into cita (id, motivo, estado, medico_id) values (?, ?, ?, ?)", citaId, "Consulta General", "CONFIRMADA", medId);

        UUID tratId = UUID.randomUUID();
        jdbcTemplate.update("insert into tratamiento (id, nombre) values (?, ?)", tratId, "Antibiotico 500mg");

        jdbcTemplate.update("insert into cita_tratamiento (cita_id, tratamiento_id) values (?, ?)", citaId, tratId);

        var citaCount = jdbcTemplate.queryForObject(
            "select count(*) from cita c " +
            "join medico m on c.medico_id = m.id " +
            "join cita_tratamiento ct on ct.cita_id = c.id " +
            "where c.id = ?",
            Integer.class, citaId
        );
        assertEquals(1, citaCount, "El modelo Salud debe persistir y consultar todas sus entidades y relaciones");
    }

    // -------------------------------------------------------------
    // HELPERS FOR MODELS AND SQL EXECUTION
    // -------------------------------------------------------------

    private void executeMigrationSql(String sqlScript) {
        // Clean drop if tables exist
        jdbcTemplate.execute("drop schema public cascade; create schema public;");
        // Remove SQL line comments
        String withoutComments = sqlScript.replaceAll("(?m)^\\s*--.*$", "");
        String[] statements = withoutComments.split(";");
        for (String stmt : statements) {
            String clean = stmt.trim();
            if (!clean.isEmpty()) {
                jdbcTemplate.execute(clean);
            }
        }
    }

    private Map<String, String> extractZip(byte[] archive) throws Exception {
        Map<String, String> files = new HashMap<>();
        try (ZipInputStream input = new ZipInputStream(new ByteArrayInputStream(archive))) {
            for (var entry = input.getNextEntry(); entry != null; entry = input.getNextEntry()) {
                byte[] content = input.readAllBytes();
                files.put(entry.getName(), new String(content, StandardCharsets.UTF_8));
            }
        }
        return files;
    }

    private DiagramDocument buildVentasModel() {
        var estadoEnum = new DiagramDocument.Enumeration(UUID.randomUUID(), "EstadoPedido", List.of(
            new DiagramDocument.EnumerationValue(UUID.randomUUID(), "NUEVO", 1),
            new DiagramDocument.EnumerationValue(UUID.randomUUID(), "PAGADO", 1)
        ), new DiagramDocument.Position(0, 0), 1);

        UUID clienteId = UUID.randomUUID();
        var clienteClass = new DiagramDocument.ClassElement(clienteId, "Cliente", List.of(
            new DiagramDocument.Attribute(UUID.randomUUID(), "id", "UUID", true, true, true),
            new DiagramDocument.Attribute(UUID.randomUUID(), "nombre", "String", false, true, false),
            new DiagramDocument.Attribute(UUID.randomUUID(), "email", "String", false, true, true)
        ), new DiagramDocument.Position(0, 0), 1);

        UUID pedidoId = UUID.randomUUID();
        var pedidoClass = new DiagramDocument.ClassElement(pedidoId, "Pedido", List.of(
            new DiagramDocument.Attribute(UUID.randomUUID(), "id", "UUID", true, true, true),
            new DiagramDocument.Attribute(UUID.randomUUID(), "numero", "String", false, true, true),
            new DiagramDocument.Attribute(UUID.randomUUID(), "total", "Decimal", false, true, false),
            new DiagramDocument.Attribute(UUID.randomUUID(), "estado", "EstadoPedido", false, true, false)
        ), new DiagramDocument.Position(0, 0), 1);

        UUID facturaId = UUID.randomUUID();
        var facturaClass = new DiagramDocument.ClassElement(facturaId, "Factura", List.of(
            new DiagramDocument.Attribute(UUID.randomUUID(), "id", "UUID", true, true, true),
            new DiagramDocument.Attribute(UUID.randomUUID(), "numeroFactura", "String", false, true, true),
            new DiagramDocument.Attribute(UUID.randomUUID(), "monto", "Decimal", false, true, false)
        ), new DiagramDocument.Position(0, 0), 1);

        UUID prodId = UUID.randomUUID();
        var productoClass = new DiagramDocument.ClassElement(prodId, "Producto", List.of(
            new DiagramDocument.Attribute(UUID.randomUUID(), "id", "UUID", true, true, true),
            new DiagramDocument.Attribute(UUID.randomUUID(), "codigo", "String", false, true, true),
            new DiagramDocument.Attribute(UUID.randomUUID(), "precio", "Decimal", false, true, false),
            new DiagramDocument.Attribute(UUID.randomUUID(), "stock", "Integer", false, true, false)
        ), new DiagramDocument.Position(0, 0), 1);

        return new DiagramDocument(
            UUID.randomUUID(), "Ventas", 1,
            List.of(clienteClass, pedidoClass, facturaClass, productoClass),
            List.of(estadoEnum),
            List.of(
                new DiagramDocument.Association(UUID.randomUUID(), clienteId, pedidoId, "1", "0..*", "pedidos", 1),
                new DiagramDocument.Association(UUID.randomUUID(), pedidoId, facturaId, "1", "0..1", "factura", null, null, "TARGET", 1),
                new DiagramDocument.Association(UUID.randomUUID(), pedidoId, prodId, "0..*", "0..*", "productos", null, null, "SOURCE", 1)
            ),
            List.of()
        );
    }

    private DiagramDocument buildColegioModel() {
        var nivelEnum = new DiagramDocument.Enumeration(UUID.randomUUID(), "NivelEducativo", List.of(
            new DiagramDocument.EnumerationValue(UUID.randomUUID(), "PRIMARIA", 1),
            new DiagramDocument.EnumerationValue(UUID.randomUUID(), "SECUNDARIA", 1)
        ), new DiagramDocument.Position(0, 0), 1);

        UUID persId = UUID.randomUUID();
        var persona = new DiagramDocument.ClassElement(persId, "Persona", List.of(
            new DiagramDocument.Attribute(UUID.randomUUID(), "id", "UUID", true, true, true),
            new DiagramDocument.Attribute(UUID.randomUUID(), "nombre", "String", false, true, false),
            new DiagramDocument.Attribute(UUID.randomUUID(), "dni", "String", false, true, true)
        ), new DiagramDocument.Position(0, 0), 1);

        UUID estId = UUID.randomUUID();
        var estudiante = new DiagramDocument.ClassElement(estId, "Estudiante", List.of(
            new DiagramDocument.Attribute(UUID.randomUUID(), "codigoEstudiante", "String", false, true, true),
            new DiagramDocument.Attribute(UUID.randomUUID(), "nivel", "NivelEducativo", false, true, false)
        ), new DiagramDocument.Position(0, 0), 1);

        UUID profId = UUID.randomUUID();
        var profesor = new DiagramDocument.ClassElement(profId, "Profesor", List.of(
            new DiagramDocument.Attribute(UUID.randomUUID(), "especialidad", "String", false, true, false),
            new DiagramDocument.Attribute(UUID.randomUUID(), "salario", "Decimal", false, true, false)
        ), new DiagramDocument.Position(0, 0), 1);

        UUID aulaId = UUID.randomUUID();
        var aula = new DiagramDocument.ClassElement(aulaId, "Aula", List.of(
            new DiagramDocument.Attribute(UUID.randomUUID(), "id", "UUID", true, true, true),
            new DiagramDocument.Attribute(UUID.randomUUID(), "numeroAula", "String", false, true, true),
            new DiagramDocument.Attribute(UUID.randomUUID(), "capacidad", "Integer", false, true, false)
        ), new DiagramDocument.Position(0, 0), 1);

        UUID cursoId = UUID.randomUUID();
        var curso = new DiagramDocument.ClassElement(cursoId, "Curso", List.of(
            new DiagramDocument.Attribute(UUID.randomUUID(), "id", "UUID", true, true, true),
            new DiagramDocument.Attribute(UUID.randomUUID(), "nombreCurso", "String", false, true, false),
            new DiagramDocument.Attribute(UUID.randomUUID(), "creditos", "Integer", false, true, false)
        ), new DiagramDocument.Position(0, 0), 1);

        return new DiagramDocument(
            UUID.randomUUID(), "Colegio", 1,
            List.of(persona, estudiante, profesor, aula, curso),
            List.of(nivelEnum),
            List.of(
                new DiagramDocument.Association(UUID.randomUUID(), profId, cursoId, "1", "0..*", "cursos", 1),
                new DiagramDocument.Association(UUID.randomUUID(), estId, cursoId, "0..*", "0..*", "cursos", null, null, "SOURCE", 1),
                new DiagramDocument.Association(UUID.randomUUID(), cursoId, aulaId, "1", "0..1", "aula", null, null, "SOURCE", 1)
            ),
            List.of(
                new DiagramDocument.Generalization(UUID.randomUUID(), persId, estId, 1),
                new DiagramDocument.Generalization(UUID.randomUUID(), persId, profId, 1)
            )
        );
    }

    private DiagramDocument buildSaludModel() {
        var estadoCitaEnum = new DiagramDocument.Enumeration(UUID.randomUUID(), "EstadoCita", List.of(
            new DiagramDocument.EnumerationValue(UUID.randomUUID(), "PENDIENTE", 1),
            new DiagramDocument.EnumerationValue(UUID.randomUUID(), "CONFIRMADA", 1)
        ), new DiagramDocument.Position(0, 0), 1);

        UUID pacId = UUID.randomUUID();
        var paciente = new DiagramDocument.ClassElement(pacId, "Paciente", List.of(
            new DiagramDocument.Attribute(UUID.randomUUID(), "id", "UUID", true, true, true),
            new DiagramDocument.Attribute(UUID.randomUUID(), "nombre", "String", false, true, false),
            new DiagramDocument.Attribute(UUID.randomUUID(), "fechaNacimiento", "Date", false, true, false)
        ), new DiagramDocument.Position(0, 0), 1);

        UUID histId = UUID.randomUUID();
        var hist = new DiagramDocument.ClassElement(histId, "HistoriaClinica", List.of(
            new DiagramDocument.Attribute(UUID.randomUUID(), "id", "UUID", true, true, true),
            new DiagramDocument.Attribute(UUID.randomUUID(), "numeroHistorial", "String", false, true, true)
        ), new DiagramDocument.Position(0, 0), 1);

        UUID medId = UUID.randomUUID();
        var medico = new DiagramDocument.ClassElement(medId, "Medico", List.of(
            new DiagramDocument.Attribute(UUID.randomUUID(), "id", "UUID", true, true, true),
            new DiagramDocument.Attribute(UUID.randomUUID(), "nombre", "String", false, true, false),
            new DiagramDocument.Attribute(UUID.randomUUID(), "colegiatura", "String", false, true, true)
        ), new DiagramDocument.Position(0, 0), 1);

        UUID citaId = UUID.randomUUID();
        var cita = new DiagramDocument.ClassElement(citaId, "Cita", List.of(
            new DiagramDocument.Attribute(UUID.randomUUID(), "id", "UUID", true, true, true),
            new DiagramDocument.Attribute(UUID.randomUUID(), "motivo", "String", false, true, false),
            new DiagramDocument.Attribute(UUID.randomUUID(), "estado", "EstadoCita", false, true, false)
        ), new DiagramDocument.Position(0, 0), 1);

        UUID tratId = UUID.randomUUID();
        var trat = new DiagramDocument.ClassElement(tratId, "Tratamiento", List.of(
            new DiagramDocument.Attribute(UUID.randomUUID(), "id", "UUID", true, true, true),
            new DiagramDocument.Attribute(UUID.randomUUID(), "nombre", "String", false, true, false)
        ), new DiagramDocument.Position(0, 0), 1);

        return new DiagramDocument(
            UUID.randomUUID(), "Salud", 1,
            List.of(paciente, hist, medico, cita, trat),
            List.of(estadoCitaEnum),
            List.of(
                new DiagramDocument.Association(UUID.randomUUID(), pacId, histId, "1", "1", "historia", null, null, "TARGET", 1),
                new DiagramDocument.Association(UUID.randomUUID(), medId, citaId, "1", "0..*", "citas", 1),
                new DiagramDocument.Association(UUID.randomUUID(), citaId, tratId, "0..*", "0..*", "tratamientos", null, null, "SOURCE", 1)
            ),
            List.of()
        );
    }
}
