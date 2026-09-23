package com.collabmodeler.api.generation;

import com.collabmodeler.api.diagram.DiagramDocument;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.tools.ToolProvider;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.*;

class BackendGeneratorTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private BackendGenerator generator;
    private MobileSpecService mobileSpecService;

    @BeforeEach
    void setUp() {
        ModelValidator validator = new ModelValidator();
        OpenApiGenerator openApiGenerator = new OpenApiGenerator();
        generator = new BackendGenerator(mapper, validator, openApiGenerator);
        mobileSpecService = new MobileSpecService(mapper, "test-secret-key-32bytes-for-hmac-sha256");
    }

    // -------------------------------------------------------------
    // 1. VALIDATION TESTS (All errors report affected element UUID)
    // -------------------------------------------------------------

    @Test
    void validationReportsAffectedClassUuidOnMissingPrimaryKey() {
        UUID classId = UUID.randomUUID();
        var model = new DiagramDocument(UUID.randomUUID(), "InvalidModel", 1,
            List.of(new DiagramDocument.ClassElement(classId, "Producto", List.of(), new DiagramDocument.Position(0, 0), 1)), List.of());

        ModelValidationException ex = assertThrows(ModelValidationException.class,
            () -> generator.generate(model, "com.example", "api"));
        assertEquals(classId, ex.getElementId(), "El error debe reportar el UUID de la clase sin clave primaria");
        assertTrue(ex.getMessage().contains(classId.toString()));
    }

    @Test
    void generatesCompositePrimaryAndForeignKeysForExplicitJoinClass() throws Exception {
        UUID saleId = UUID.randomUUID(), productId = UUID.randomUUID(), detailId = UUID.randomUUID();
        var sale = new DiagramDocument.ClassElement(saleId, "Venta", List.of(
            new DiagramDocument.Attribute(UUID.randomUUID(), "id", "UUID", true, true, true)), new DiagramDocument.Position(0, 0), 1);
        var product = new DiagramDocument.ClassElement(productId, "Producto", List.of(
            new DiagramDocument.Attribute(UUID.randomUUID(), "id", "UUID", true, true, true)), new DiagramDocument.Position(300, 0), 1);
        var detail = new DiagramDocument.ClassElement(detailId, "Venta_Producto", List.of(
            new DiagramDocument.Attribute(UUID.randomUUID(), "Venta_id", "UUID", true, true, false),
            new DiagramDocument.Attribute(UUID.randomUUID(), "Producto_id", "UUID", true, true, false)),
            new DiagramDocument.Position(150, 220), 1);
        var links = List.of(
            new DiagramDocument.Association(UUID.randomUUID(), saleId, detailId, "1", "0..*", null, 1),
            new DiagramDocument.Association(UUID.randomUUID(), productId, detailId, "1", "0..*", null, 1));
        var model = new DiagramDocument(UUID.randomUUID(), "Ventas", 1, List.of(sale, product, detail), links);
        var generated = extractZip(generator.generate(model, "com.example", "ventas-api"));
        String sql = generated.get("src/main/resources/db/migration/V1__initial_schema.sql");
        assertTrue(sql.contains("primary key (venta_id, producto_id)"));
        assertTrue(sql.contains("foreign key (venta_id) references venta(id)"));
        assertTrue(sql.contains("foreign key (producto_id) references producto(id)"));
        assertEquals(1, sql.split("venta_id uuid", -1).length - 1);
        assertEquals(1, sql.split("producto_id uuid", -1).length - 1);
        String entity = generated.get("src/main/java/com/example/ventas_api/model/Venta_Producto.java");
        assertTrue(entity.contains("@IdClass(Venta_ProductoId.class)"));
        assertTrue(entity.contains("insertable = false, updatable = false"));
        assertTrue(generated.get("src/main/java/com/example/ventas_api/mapper/Venta_ProductoMapper.java")
            .contains("entity.setVenta_id(dto.getVentaId())"));
        compileExtractedJava(generated);
    }

    @Test
    void validationReportsAffectedAttributeUuidOnUnknownType() {
        UUID attrId = UUID.randomUUID();
        var pk = new DiagramDocument.Attribute(UUID.randomUUID(), "id", "UUID", true, true, true);
        var badAttr = new DiagramDocument.Attribute(attrId, "extra", "NonExistentType", false, false, false);
        var model = new DiagramDocument(UUID.randomUUID(), "InvalidTypeModel", 1,
            List.of(new DiagramDocument.ClassElement(UUID.randomUUID(), "Item", List.of(pk, badAttr), new DiagramDocument.Position(0, 0), 1)), List.of());

        ModelValidationException ex = assertThrows(ModelValidationException.class,
            () -> generator.generate(model, "com.example", "api"));
        assertEquals(attrId, ex.getElementId(), "El error debe reportar el UUID del atributo con tipo desconocido");
    }

    @Test
    void validationReportsAffectedClassUuidOnDuplicateClassName() {
        UUID classId1 = UUID.randomUUID();
        UUID classId2 = UUID.randomUUID();
        var pk1 = new DiagramDocument.Attribute(UUID.randomUUID(), "id", "UUID", true, true, true);
        var pk2 = new DiagramDocument.Attribute(UUID.randomUUID(), "id", "UUID", true, true, true);
        var model = new DiagramDocument(UUID.randomUUID(), "DuplicateClass", 1,
            List.of(
                new DiagramDocument.ClassElement(classId1, "Cliente", List.of(pk1), new DiagramDocument.Position(0, 0), 1),
                new DiagramDocument.ClassElement(classId2, "cliente", List.of(pk2), new DiagramDocument.Position(0, 0), 1)
            ), List.of());

        ModelValidationException ex = assertThrows(ModelValidationException.class,
            () -> generator.generate(model, "com.example", "api"));
        assertEquals(classId2, ex.getElementId(), "El error debe reportar el UUID de la clase duplicada");
    }

    @Test
    void validationReportsAffectedAttributeUuidOnDuplicateAttributeName() {
        UUID attrId1 = UUID.randomUUID();
        UUID attrId2 = UUID.randomUUID();
        var pk = new DiagramDocument.Attribute(attrId1, "id", "UUID", true, true, true);
        var attr2 = new DiagramDocument.Attribute(attrId2, "id", "String", false, false, false);
        var model = new DiagramDocument(UUID.randomUUID(), "DuplicateAttr", 1,
            List.of(new DiagramDocument.ClassElement(UUID.randomUUID(), "Cliente", List.of(pk, attr2), new DiagramDocument.Position(0, 0), 1)), List.of());

        ModelValidationException ex = assertThrows(ModelValidationException.class,
            () -> generator.generate(model, "com.example", "api"));
        assertEquals(attrId2, ex.getElementId(), "El error debe reportar el UUID del atributo duplicado");
    }

    @Test
    void validationReportsAffectedGeneralizationUuidOnCycle() {
        UUID classA = UUID.randomUUID();
        UUID classB = UUID.randomUUID();
        UUID gen1 = UUID.randomUUID();
        UUID gen2 = UUID.randomUUID();

        var pkA = new DiagramDocument.Attribute(UUID.randomUUID(), "id", "UUID", true, true, true);
        var pkB = new DiagramDocument.Attribute(UUID.randomUUID(), "id", "UUID", true, true, true);

        var model = new DiagramDocument(UUID.randomUUID(), "CycleModel", 1,
            List.of(
                new DiagramDocument.ClassElement(classA, "ClaseA", List.of(pkA), new DiagramDocument.Position(0, 0), 1),
                new DiagramDocument.ClassElement(classB, "ClaseB", List.of(pkB), new DiagramDocument.Position(0, 0), 1)
            ),
            List.of(),
            List.of(),
            List.of(
                new DiagramDocument.Generalization(gen1, classA, classB, 1),
                new DiagramDocument.Generalization(gen2, classB, classA, 1)
            )
        );

        ModelValidationException ex = assertThrows(ModelValidationException.class,
            () -> generator.generate(model, "com.example", "api"));
        assertTrue(ex.getMessage().contains("Ciclo de herencia"));
    }

    @Test
    void validationReportsAffectedAssociationUuidOnMissingReference() {
        UUID assocId = UUID.randomUUID();
        UUID missingClassId = UUID.randomUUID();
        UUID validClassId = UUID.randomUUID();
        var pk = new DiagramDocument.Attribute(UUID.randomUUID(), "id", "UUID", true, true, true);

        var model = new DiagramDocument(UUID.randomUUID(), "BadAssoc", 1,
            List.of(new DiagramDocument.ClassElement(validClassId, "Valida", List.of(pk), new DiagramDocument.Position(0, 0), 1)),
            List.of(),
            List.of(new DiagramDocument.Association(assocId, validClassId, missingClassId, "1", "0..*", "relacion", 1)),
            List.of()
        );

        ModelValidationException ex = assertThrows(ModelValidationException.class,
            () -> generator.generate(model, "com.example", "api"));
        assertEquals(assocId, ex.getElementId(), "El error debe reportar el UUID de la asociación rota");
    }

    @Test
    void validationReportsAffectedAssociationUuidOnInvalidCardinality() {
        UUID assocId = UUID.randomUUID();
        UUID classA = UUID.randomUUID();
        UUID classB = UUID.randomUUID();
        var pkA = new DiagramDocument.Attribute(UUID.randomUUID(), "id", "UUID", true, true, true);
        var pkB = new DiagramDocument.Attribute(UUID.randomUUID(), "id", "UUID", true, true, true);

        var model = new DiagramDocument(UUID.randomUUID(), "BadCard", 1,
            List.of(
                new DiagramDocument.ClassElement(classA, "ClaseA", List.of(pkA), new DiagramDocument.Position(0, 0), 1),
                new DiagramDocument.ClassElement(classB, "ClaseB", List.of(pkB), new DiagramDocument.Position(0, 0), 1)
            ),
            List.of(),
            List.of(new DiagramDocument.Association(assocId, classA, classB, "invalid_card", "1", "rel", 1)),
            List.of()
        );

        ModelValidationException ex = assertThrows(ModelValidationException.class,
            () -> generator.generate(model, "com.example", "api"));
        assertEquals(assocId, ex.getElementId(), "El error debe reportar el UUID de la asociación con cardinalidad inválida");
    }

    // -------------------------------------------------------------
    // 2. VENTAS (Sales) MODEL COMPILATION AND TRACEABILITY
    // -------------------------------------------------------------

    @Test
    void generatesAndCompilesVentasModel() throws Exception {
        UUID estadoEnumId = UUID.randomUUID();
        var enumValues = List.of(
            new DiagramDocument.EnumerationValue(UUID.randomUUID(), "NUEVO", 1),
            new DiagramDocument.EnumerationValue(UUID.randomUUID(), "PAGADO", 1),
            new DiagramDocument.EnumerationValue(UUID.randomUUID(), "CANCELADO", 1)
        );
        var estadoEnum = new DiagramDocument.Enumeration(estadoEnumId, "EstadoPedido", enumValues, new DiagramDocument.Position(0, 0), 1);

        UUID clienteId = UUID.randomUUID();
        var clientePk = new DiagramDocument.Attribute(UUID.randomUUID(), "id", "UUID", true, true, true);
        var clienteNombre = new DiagramDocument.Attribute(UUID.randomUUID(), "nombre", "String", false, true, false);
        var clienteEmail = new DiagramDocument.Attribute(UUID.randomUUID(), "email", "String", false, true, true);
        var clienteClass = new DiagramDocument.ClassElement(clienteId, "Cliente", List.of(clientePk, clienteNombre, clienteEmail), new DiagramDocument.Position(0, 0), 1);

        UUID pedidoId = UUID.randomUUID();
        var pedidoPk = new DiagramDocument.Attribute(UUID.randomUUID(), "id", "UUID", true, true, true);
        var pedidoNumero = new DiagramDocument.Attribute(UUID.randomUUID(), "numero", "String", false, true, true);
        var pedidoTotal = new DiagramDocument.Attribute(UUID.randomUUID(), "total", "Decimal", false, true, false);
        var pedidoEstado = new DiagramDocument.Attribute(UUID.randomUUID(), "estado", "EstadoPedido", false, true, false);
        var pedidoClass = new DiagramDocument.ClassElement(pedidoId, "Pedido", List.of(pedidoPk, pedidoNumero, pedidoTotal, pedidoEstado), new DiagramDocument.Position(0, 0), 1);

        UUID facturaId = UUID.randomUUID();
        var facturaPk = new DiagramDocument.Attribute(UUID.randomUUID(), "id", "UUID", true, true, true);
        var facturaNumero = new DiagramDocument.Attribute(UUID.randomUUID(), "numeroFactura", "String", false, true, true);
        var facturaMonto = new DiagramDocument.Attribute(UUID.randomUUID(), "monto", "Decimal", false, true, false);
        var facturaClass = new DiagramDocument.ClassElement(facturaId, "Factura", List.of(facturaPk, facturaNumero, facturaMonto), new DiagramDocument.Position(0, 0), 1);

        UUID productoId = UUID.randomUUID();
        var productoPk = new DiagramDocument.Attribute(UUID.randomUUID(), "id", "UUID", true, true, true);
        var productoCodigo = new DiagramDocument.Attribute(UUID.randomUUID(), "codigo", "String", false, true, true);
        var productoPrecio = new DiagramDocument.Attribute(UUID.randomUUID(), "precio", "Decimal", false, true, false);
        var productoStock = new DiagramDocument.Attribute(UUID.randomUUID(), "stock", "Integer", false, true, false);
        var productoClass = new DiagramDocument.ClassElement(productoId, "Producto", List.of(productoPk, productoCodigo, productoPrecio, productoStock), new DiagramDocument.Position(0, 0), 1);

        // 1:N Cliente -> Pedido
        UUID assocClientePedido = UUID.randomUUID();
        var rel1 = new DiagramDocument.Association(assocClientePedido, clienteId, pedidoId, "1", "0..*", "pedidos", 1);

        // 1:1 Pedido -> Factura
        UUID assocPedidoFactura = UUID.randomUUID();
        var rel2 = new DiagramDocument.Association(assocPedidoFactura, pedidoId, facturaId, "1", "0..1", "factura", null, null, "SOURCE", 1);

        // N:M Pedido -> Producto
        UUID assocPedidoProducto = UUID.randomUUID();
        var rel3 = new DiagramDocument.Association(assocPedidoProducto, pedidoId, productoId, "0..*", "0..*", "productos", null, null, "SOURCE", 1);

        var ventasModel = new DiagramDocument(
            UUID.randomUUID(), "Ventas", 1,
            List.of(clienteClass, pedidoClass, facturaClass, productoClass),
            List.of(estadoEnum),
            List.of(rel1, rel2, rel3),
            List.of()
        );

        byte[] zip = generator.generate(ventasModel, "com.example", "ventas-api");
        assertNotNull(zip);

        Map<String, String> extracted = extractZip(zip);

        // Verify key structural files
        assertTrue(extracted.containsKey("pom.xml"));
        assertTrue(extracted.containsKey("Dockerfile"));
        assertTrue(extracted.containsKey("docker-compose.yml"));
        assertTrue(extracted.containsKey("README.md"));
        assertTrue(extracted.containsKey("openapi.yaml"));
        assertTrue(extracted.containsKey("model-traceability.json"));
        assertTrue(extracted.containsKey("src/main/resources/db/migration/V1__initial_schema.sql"));

        // Verify entities and isolated auth
        assertTrue(extracted.containsKey("src/main/java/com/example/ventas_api/model/Cliente.java"));
        assertTrue(extracted.containsKey("src/main/java/com/example/ventas_api/model/Pedido.java"));
        assertTrue(extracted.containsKey("src/main/java/com/example/ventas_api/model/Factura.java"));
        assertTrue(extracted.containsKey("src/main/java/com/example/ventas_api/model/Producto.java"));
        assertTrue(extracted.containsKey("src/main/java/com/example/ventas_api/model/EstadoPedido.java"));
        assertTrue(extracted.containsKey("src/main/java/com/example/ventas_api/auth/AuthUser.java"));
        assertTrue(extracted.containsKey("src/main/java/com/example/ventas_api/auth/AuthController.java"));

        // Check @ModelElement annotation presence
        String pedidoCode = extracted.get("src/main/java/com/example/ventas_api/model/Pedido.java");
        assertTrue(pedidoCode.contains("@ModelElement(\"" + pedidoId + "\")"));
        assertTrue(pedidoCode.contains("@ModelElement(\"" + pedidoTotal.id() + "\")"));

        // Check EnumType.STRING on estado
        assertTrue(pedidoCode.contains("@Enumerated(EnumType.STRING)"));

        // Check traceability content
        String traceJson = extracted.get("model-traceability.json");
        assertTrue(traceJson.contains(clienteId.toString()));
        assertTrue(traceJson.contains(pedidoId.toString()));
        assertTrue(traceJson.contains(estadoEnumId.toString()));
        assertTrue(traceJson.contains(assocClientePedido.toString()));

        // Compile Java sources
        compileExtractedJava(extracted);
    }

    // -------------------------------------------------------------
    // 3. COLEGIO (School) MODEL: JOINED INHERITANCE, 1:N, N:M, 1:1
    // -------------------------------------------------------------

    @Test
    void generatesAndCompilesColegioModelWithJoinedInheritance() throws Exception {
        UUID nivelEnumId = UUID.randomUUID();
        var nivelEnum = new DiagramDocument.Enumeration(nivelEnumId, "NivelEducativo", List.of(
            new DiagramDocument.EnumerationValue(UUID.randomUUID(), "PRIMARIA", 1),
            new DiagramDocument.EnumerationValue(UUID.randomUUID(), "SECUNDARIA", 1)
        ), new DiagramDocument.Position(0, 0), 1);

        UUID personaId = UUID.randomUUID();
        var personaPk = new DiagramDocument.Attribute(UUID.randomUUID(), "id", "UUID", true, true, true);
        var personaNombre = new DiagramDocument.Attribute(UUID.randomUUID(), "nombre", "String", false, true, false);
        var personaDni = new DiagramDocument.Attribute(UUID.randomUUID(), "dni", "String", false, true, true);
        var personaClass = new DiagramDocument.ClassElement(personaId, "Persona", List.of(personaPk, personaNombre, personaDni), new DiagramDocument.Position(0, 0), 1);

        UUID estudianteId = UUID.randomUUID();
        var estCodigo = new DiagramDocument.Attribute(UUID.randomUUID(), "codigoEstudiante", "String", false, true, true);
        var estNivel = new DiagramDocument.Attribute(UUID.randomUUID(), "nivel", "NivelEducativo", false, true, false);
        var estudianteClass = new DiagramDocument.ClassElement(estudianteId, "Estudiante", List.of(estCodigo, estNivel), new DiagramDocument.Position(0, 0), 1);

        UUID profesorId = UUID.randomUUID();
        var profEsp = new DiagramDocument.Attribute(UUID.randomUUID(), "especialidad", "String", false, true, false);
        var profSalario = new DiagramDocument.Attribute(UUID.randomUUID(), "salario", "Decimal", false, true, false);
        var profesorClass = new DiagramDocument.ClassElement(profesorId, "Profesor", List.of(profEsp, profSalario), new DiagramDocument.Position(0, 0), 1);

        UUID cursoId = UUID.randomUUID();
        var cursoPk = new DiagramDocument.Attribute(UUID.randomUUID(), "id", "UUID", true, true, true);
        var cursoNombre = new DiagramDocument.Attribute(UUID.randomUUID(), "nombreCurso", "String", false, true, false);
        var cursoCreditos = new DiagramDocument.Attribute(UUID.randomUUID(), "creditos", "Integer", false, true, false);
        var cursoClass = new DiagramDocument.ClassElement(cursoId, "Curso", List.of(cursoPk, cursoNombre, cursoCreditos), new DiagramDocument.Position(0, 0), 1);

        UUID aulaId = UUID.randomUUID();
        var aulaPk = new DiagramDocument.Attribute(UUID.randomUUID(), "id", "UUID", true, true, true);
        var aulaNum = new DiagramDocument.Attribute(UUID.randomUUID(), "numeroAula", "String", false, true, true);
        var aulaCapacidad = new DiagramDocument.Attribute(UUID.randomUUID(), "capacidad", "Integer", false, true, false);
        var aulaClass = new DiagramDocument.ClassElement(aulaId, "Aula", List.of(aulaPk, aulaNum, aulaCapacidad), new DiagramDocument.Position(0, 0), 1);

        // Generalizations (JOINED inheritance)
        UUID genEst = UUID.randomUUID();
        UUID genProf = UUID.randomUUID();
        var gen1 = new DiagramDocument.Generalization(genEst, personaId, estudianteId, 1);
        var gen2 = new DiagramDocument.Generalization(genProf, personaId, profesorId, 1);

        // 1:N Profesor -> Curso
        var relProfCurso = new DiagramDocument.Association(UUID.randomUUID(), profesorId, cursoId, "1", "0..*", "cursos", 1);
        // N:M Estudiante -> Curso
        var relEstCurso = new DiagramDocument.Association(UUID.randomUUID(), estudianteId, cursoId, "0..*", "0..*", "cursos", null, null, "SOURCE", 1);
        // 1:1 Curso -> Aula
        var relCursoAula = new DiagramDocument.Association(UUID.randomUUID(), cursoId, aulaId, "1", "0..1", "aula", null, null, "SOURCE", 1);

        var colegioModel = new DiagramDocument(
            UUID.randomUUID(), "Colegio", 1,
            List.of(personaClass, estudianteClass, profesorClass, cursoClass, aulaClass),
            List.of(nivelEnum),
            List.of(relProfCurso, relEstCurso, relCursoAula),
            List.of(gen1, gen2)
        );

        byte[] zip = generator.generate(colegioModel, "com.example", "colegio-api");
        assertNotNull(zip);

        Map<String, String> extracted = extractZip(zip);

        // Verify JOINED inheritance in code
        String personaCode = extracted.get("src/main/java/com/example/colegio_api/model/Persona.java");
        assertTrue(personaCode.contains("@Inheritance(strategy = InheritanceType.JOINED)"));

        String estCode = extracted.get("src/main/java/com/example/colegio_api/model/Estudiante.java");
        assertTrue(estCode.contains("extends Persona"));
        assertTrue(estCode.contains("@PrimaryKeyJoinColumn(name = \"id\")"));

        String profCode = extracted.get("src/main/java/com/example/colegio_api/model/Profesor.java");
        assertTrue(profCode.contains("extends Persona"));

        // Compile Java sources
        compileExtractedJava(extracted);
    }

    // -------------------------------------------------------------
    // 4. SALUD (Health) MODEL COMPILATION AND VERIFICATION
    // -------------------------------------------------------------

    @Test
    void generatesAndCompilesSaludModel() throws Exception {
        UUID estadoCitaId = UUID.randomUUID();
        var estadoCitaEnum = new DiagramDocument.Enumeration(estadoCitaId, "EstadoCita", List.of(
            new DiagramDocument.EnumerationValue(UUID.randomUUID(), "PENDIENTE", 1),
            new DiagramDocument.EnumerationValue(UUID.randomUUID(), "CONFIRMADA", 1),
            new DiagramDocument.EnumerationValue(UUID.randomUUID(), "ATENDIDA", 1)
        ), new DiagramDocument.Position(0, 0), 1);

        UUID pacienteId = UUID.randomUUID();
        var pacientePk = new DiagramDocument.Attribute(UUID.randomUUID(), "id", "UUID", true, true, true);
        var pacienteNombre = new DiagramDocument.Attribute(UUID.randomUUID(), "nombre", "String", false, true, false);
        var pacienteFechaNac = new DiagramDocument.Attribute(UUID.randomUUID(), "fechaNacimiento", "Date", false, true, false);
        var pacienteClass = new DiagramDocument.ClassElement(pacienteId, "Paciente", List.of(pacientePk, pacienteNombre, pacienteFechaNac), new DiagramDocument.Position(0, 0), 1);

        UUID medicoId = UUID.randomUUID();
        var medicoPk = new DiagramDocument.Attribute(UUID.randomUUID(), "id", "UUID", true, true, true);
        var medicoNombre = new DiagramDocument.Attribute(UUID.randomUUID(), "nombre", "String", false, true, false);
        var medicoColegiatura = new DiagramDocument.Attribute(UUID.randomUUID(), "colegiatura", "String", false, true, true);
        var medicoClass = new DiagramDocument.ClassElement(medicoId, "Medico", List.of(medicoPk, medicoNombre, medicoColegiatura), new DiagramDocument.Position(0, 0), 1);

        UUID citaId = UUID.randomUUID();
        var citaPk = new DiagramDocument.Attribute(UUID.randomUUID(), "id", "UUID", true, true, true);
        var citaFecha = new DiagramDocument.Attribute(UUID.randomUUID(), "fechaHora", "DateTime", false, true, false);
        var citaMotivo = new DiagramDocument.Attribute(UUID.randomUUID(), "motivo", "String", false, true, false);
        var citaEstado = new DiagramDocument.Attribute(UUID.randomUUID(), "estado", "EstadoCita", false, true, false);
        var citaClass = new DiagramDocument.ClassElement(citaId, "Cita", List.of(citaPk, citaFecha, citaMotivo, citaEstado), new DiagramDocument.Position(0, 0), 1);

        UUID histId = UUID.randomUUID();
        var histPk = new DiagramDocument.Attribute(UUID.randomUUID(), "id", "UUID", true, true, true);
        var histNumero = new DiagramDocument.Attribute(UUID.randomUUID(), "numeroHistorial", "String", false, true, true);
        var histClass = new DiagramDocument.ClassElement(histId, "HistoriaClinica", List.of(histPk, histNumero), new DiagramDocument.Position(0, 0), 1);

        UUID tratamientoId = UUID.randomUUID();
        var tratPk = new DiagramDocument.Attribute(UUID.randomUUID(), "id", "UUID", true, true, true);
        var tratNombre = new DiagramDocument.Attribute(UUID.randomUUID(), "nombre", "String", false, true, false);
        var tratClass = new DiagramDocument.ClassElement(tratamientoId, "Tratamiento", List.of(tratPk, tratNombre), new DiagramDocument.Position(0, 0), 1);

        // 1:N Medico -> Cita
        var relMedicoCita = new DiagramDocument.Association(UUID.randomUUID(), medicoId, citaId, "1", "0..*", "citas", 1);
        // 1:1 Paciente -> HistoriaClinica
        var relPacHist = new DiagramDocument.Association(UUID.randomUUID(), pacienteId, histId, "1", "1", "historia", null, null, "SOURCE", 1);
        // N:M Cita -> Tratamiento
        var relCitaTrat = new DiagramDocument.Association(UUID.randomUUID(), citaId, tratamientoId, "0..*", "0..*", "tratamientos", null, null, "SOURCE", 1);

        var saludModel = new DiagramDocument(
            UUID.randomUUID(), "Salud", 1,
            List.of(pacienteClass, medicoClass, citaClass, histClass, tratClass),
            List.of(estadoCitaEnum),
            List.of(relMedicoCita, relPacHist, relCitaTrat),
            List.of()
        );

        byte[] zip = generator.generate(saludModel, "com.example", "salud-api");
        assertNotNull(zip);

        Map<String, String> extracted = extractZip(zip);
        assertTrue(extracted.containsKey("src/main/java/com/example/salud_api/model/Paciente.java"));
        assertTrue(extracted.containsKey("src/main/java/com/example/salud_api/model/HistoriaClinica.java"));

        compileExtractedJava(extracted);
    }

    // -------------------------------------------------------------
    // 5. SIGNED MOBILE SPEC AND PATH TRAVERSAL PROTECTION
    // -------------------------------------------------------------

    @Test
    void generatesAndVerifiesSignedMobileSpec() throws Exception {
        var pk = new DiagramDocument.Attribute(UUID.randomUUID(), "id", "UUID", true, true, true);
        var model = new DiagramDocument(UUID.randomUUID(), "Ventas", 3,
            List.of(new DiagramDocument.ClassElement(UUID.randomUUID(), "Producto", List.of(pk), new DiagramDocument.Position(0, 0), 1)), List.of());

        String specJson = mobileSpecService.generateSpecJson(model, UUID.randomUUID(), "openapi: 3.0.3\ninfo:\n  title: Test\n");
        assertNotNull(specJson);
        assertTrue(specJson.contains("\"signature\""));
        assertTrue(specJson.contains("\"revision\" : 3"));

        Map<String, Object> parsed = mapper.readValue(specJson, Map.class);
        assertTrue(mobileSpecService.verifySignature(parsed), "La firma del spec debe ser válida");

        // Tampered spec should fail verification
        parsed.put("revision", 999);
        assertFalse(mobileSpecService.verifySignature(parsed), "Un spec alterado debe fallar la verificación de firma");
    }

    // -------------------------------------------------------------
    // HELPERS
    // -------------------------------------------------------------

    private Map<String, String> extractZip(byte[] archive) throws Exception {
        Map<String, String> files = new HashMap<>();
        try (ZipInputStream input = new ZipInputStream(new ByteArrayInputStream(archive))) {
            for (var entry = input.getNextEntry(); entry != null; entry = input.getNextEntry()) {
                byte[] content = input.readAllBytes();
                files.put(entry.getName(), new String(content, java.nio.charset.StandardCharsets.UTF_8));
            }
        }
        return files;
    }

    private void compileExtractedJava(Map<String, String> files) throws Exception {
        Path root = Files.createTempDirectory("generated-backend-test-");
        var sources = new ArrayList<String>();

        for (var entry : files.entrySet()) {
            if (!entry.getKey().endsWith(".java")) continue;
            Path target = root.resolve(entry.getKey());
            Files.createDirectories(target.getParent());
            Files.writeString(target, entry.getValue());
            sources.add(target.toString());
        }

        var arguments = new ArrayList<String>();
        arguments.add("-classpath");
        arguments.add(System.getProperty("java.class.path"));
        arguments.add("-d");
        arguments.add(root.resolve("classes").toString());
        Files.createDirectories(root.resolve("classes"));
        arguments.addAll(sources);

        int exitCode = ToolProvider.getSystemJavaCompiler().run(null, null, null, arguments.toArray(String[]::new));
        assertEquals(0, exitCode, "Todos los archivos Java generados deben compilar exitosamente con javac");
    }
}
