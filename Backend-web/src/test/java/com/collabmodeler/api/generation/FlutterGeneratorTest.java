package com.collabmodeler.api.generation;

import com.collabmodeler.api.diagram.DiagramDocument;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.*;

class FlutterGeneratorTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private FlutterGenerator flutterGenerator;
    private OpenApiGenerator openApiGenerator;
    private MobileSpecService mobileSpecService;

    @BeforeEach
    void setUp() {
        ModelValidator validator = new ModelValidator();
        flutterGenerator = new FlutterGenerator(mapper, validator);
        openApiGenerator = new OpenApiGenerator();
        mobileSpecService = new MobileSpecService(mapper, "test-secret-key-32bytes-for-hmac-sha256");
    }

    // =========================================================================
    // 1. VENTAS MODEL GENERATION & LAYER INTEGRITY
    // =========================================================================

    @Test
    void generatesFlutterAppForVentasModel() throws Exception {
        var ventasModel = buildVentasModel();
        String openapi = openApiGenerator.generateYaml(ventasModel, "ventas-api");

        Map<String, String> files = flutterGenerator.generateFiles(ventasModel, openapi, "Ventas Móvil");
        assertNotNull(files);

        // Core build files
        assertTrue(files.containsKey("pubspec.yaml"));
        assertTrue(files.containsKey("analysis_options.yaml"));
        assertTrue(files.containsKey("README.md"));

        // README must include adb reverse instructions and localhost:8080 default
        String readme = files.get("README.md");
        assertTrue(readme.contains("adb reverse tcp:8080 tcp:8080"));
        assertTrue(readme.contains("localhost:8080"));
        assertTrue(readme.contains("flutter analyze"));
        assertTrue(readme.contains("flutter test"));

        // Core layers
        assertTrue(files.containsKey("lib/core/config/app_config.dart"));
        assertTrue(files.containsKey("lib/core/api/api_client.dart"));
        assertTrue(files.containsKey("lib/core/api/api_exception.dart"));
        assertTrue(files.containsKey("lib/core/storage/secure_storage_service.dart"));
        assertTrue(files.containsKey("lib/core/database/app_database.dart"));
        assertTrue(files.containsKey("lib/core/sync/outbox_service.dart"));
        assertTrue(files.containsKey("lib/core/sync/sync_service.dart"));
        assertTrue(files.containsKey("lib/core/theme/app_theme.dart"));
        assertTrue(files.containsKey("lib/core/i18n/app_strings.dart"));
        assertTrue(files.containsKey("lib/core/widgets/async_state_view.dart"));
        assertTrue(files.containsKey("lib/core/widgets/confirm_dialog.dart"));
        assertTrue(files.containsKey("lib/core/widgets/relation_picker.dart"));
        assertTrue(files.containsKey("lib/core/widgets/sync_status_badge.dart"));

        // AppDatabase must define cached_entities, outbox_operations and conflict_records
        String dbCode = files.get("lib/core/database/app_database.dart");
        assertTrue(dbCode.contains("cached_entities"));
        assertTrue(dbCode.contains("outbox_operations"));
        assertTrue(dbCode.contains("conflict_records"));

        // Outbox & Sync services must provide transactional CRUD and 3-way conflict handling
        String outboxCode = files.get("lib/core/sync/outbox_service.dart");
        assertTrue(outboxCode.contains("enqueueCreate"));
        assertTrue(outboxCode.contains("enqueueUpdate"));
        assertTrue(outboxCode.contains("enqueueDelete"));

        String syncCode = files.get("lib/core/sync/sync_service.dart");
        assertTrue(syncCode.contains("syncUp"));
        assertTrue(syncCode.contains("syncDown"));
        assertTrue(syncCode.contains("resolveConflictKeepLocal"));
        assertTrue(syncCode.contains("resolveConflictDiscardLocal"));
        assertTrue(syncCode.contains("resolveConflictManual"));

        // Conflict resolution screen
        assertTrue(files.containsKey("lib/presentation/screens/conflicts/conflict_resolution_screen.dart"));
        String conflictScreen = files.get("lib/presentation/screens/conflicts/conflict_resolution_screen.dart");
        assertTrue(conflictScreen.contains("ConflictResolutionScreen"));
        assertTrue(conflictScreen.contains("Conservar local"));
        assertTrue(conflictScreen.contains("Descartar local"));

        // AppConfig must have --dart-define API_BASE_URL default
        String appConfig = files.get("lib/core/config/app_config.dart");
        assertTrue(appConfig.contains("String.fromEnvironment"));
        assertTrue(appConfig.contains("http://localhost:8080"));
        assertTrue(appConfig.contains("SharedPreferences"));

        // Auth
        assertTrue(files.containsKey("lib/data/models/auth_models.dart"));
        assertTrue(files.containsKey("lib/data/repositories/auth_repository.dart"));
        assertTrue(files.containsKey("lib/presentation/state/auth_provider.dart"));
        assertTrue(files.containsKey("lib/presentation/screens/auth/login_screen.dart"));
        assertTrue(files.containsKey("lib/presentation/screens/auth/register_screen.dart"));
        assertTrue(files.containsKey("lib/presentation/screens/settings/server_settings_screen.dart"));

        // Enums
        assertTrue(files.containsKey("lib/data/models/estado_pedido.dart"));
        String enumCode = files.get("lib/data/models/estado_pedido.dart");
        assertTrue(enumCode.contains("enum EstadoPedido"));
        assertTrue(enumCode.contains("NUEVO"));
        assertTrue(enumCode.contains("PAGADO"));
        assertTrue(enumCode.contains("CANCELADO"));

        // Domain models
        assertTrue(files.containsKey("lib/data/models/cliente.dart"));
        assertTrue(files.containsKey("lib/data/models/pedido.dart"));
        assertTrue(files.containsKey("lib/data/models/factura.dart"));
        assertTrue(files.containsKey("lib/data/models/producto.dart"));

        String pedidoModel = files.get("lib/data/models/pedido.dart");
        assertTrue(pedidoModel.contains("class Pedido"));
        assertTrue(pedidoModel.contains("EstadoPedido estado"));
        assertTrue(pedidoModel.contains("String? clienteId"));
        assertTrue(pedidoModel.contains("String? facturaId"));
        assertTrue(pedidoModel.contains("List<String> productoIds"));

        // Repositories must be offline-first with AppDatabase & OutboxService
        assertTrue(files.containsKey("lib/data/repositories/pedido_repository.dart"));
        String pedidoRepo = files.get("lib/data/repositories/pedido_repository.dart");
        assertTrue(pedidoRepo.contains("AppDatabase"));
        assertTrue(pedidoRepo.contains("OutboxService"));
        assertTrue(pedidoRepo.contains("enqueueCreate"));
        assertTrue(pedidoRepo.contains("enqueueUpdate"));
        assertTrue(pedidoRepo.contains("enqueueDelete"));

        // Providers
        assertTrue(files.containsKey("lib/presentation/state/pedido_provider.dart"));
        String pedidoProvider = files.get("lib/presentation/state/pedido_provider.dart");
        assertTrue(pedidoProvider.contains("class PedidoProvider extends ChangeNotifier"));

        // CRUD Screens
        assertTrue(files.containsKey("lib/presentation/screens/pedido/pedido_list_screen.dart"));
        assertTrue(files.containsKey("lib/presentation/screens/pedido/pedido_detail_screen.dart"));
        assertTrue(files.containsKey("lib/presentation/screens/pedido/pedido_form_screen.dart"));

        // Form screen should include relation dropdown / picker for Cliente
        String formScreen = files.get("lib/presentation/screens/pedido/pedido_form_screen.dart");
        assertTrue(formScreen.contains("RelationDropdown"));
        assertTrue(formScreen.contains("Seleccionar Cliente"));

        // Home and Main
        assertTrue(files.containsKey("lib/presentation/screens/home/home_screen.dart"));
        String homeScreenCode = files.get("lib/presentation/screens/home/home_screen.dart");
        assertTrue(homeScreenCode.contains("SyncStatusBadge"));
        assertTrue(homeScreenCode.contains("AiAssistantScreen"));

        assertTrue(files.containsKey("lib/core/ai/ai_command_interpreter.dart"));
        assertTrue(files.containsKey("lib/core/ai/local_ocr_service.dart"));
        assertTrue(files.containsKey("lib/core/ai/remote_ai_service.dart"));
        assertTrue(files.containsKey("lib/core/ai/speech_recognition_service.dart"));
        assertTrue(files.containsKey("lib/presentation/screens/ai/ai_assistant_screen.dart"));
        assertTrue(files.get("lib/core/ai/local_ocr_service.dart").contains("TextRecognizer"));
        assertFalse(files.get("lib/core/ai/local_ocr_service.dart").contains("Texto extraído de imagen"));
        assertTrue(files.get("pubspec.yaml").contains("google_mlkit_text_recognition"));

        assertTrue(files.containsKey("lib/main.dart"));
        String mainCode = files.get("lib/main.dart");
        assertTrue(mainCode.contains("AppDatabase.instance.database"));
        assertTrue(mainCode.contains("SyncService.instance.init"));

        // Tests
        assertTrue(files.containsKey("test/model_test.dart"));
        assertTrue(files.containsKey("test/widget_test.dart"));
        assertTrue(files.containsKey("test/offline_sync_test.dart"));
        String syncTestCode = files.get("test/offline_sync_test.dart");
        assertTrue(syncTestCode.contains("Offline-first & Synchronization Tests"));
    }

    // =========================================================================
    // 2. COLEGIO MODEL: INHERITANCE AND RELATIONS
    // =========================================================================

    @Test
    void generatesFlutterAppForColegioModelWithInheritance() throws Exception {
        var colegioModel = buildColegioModel();
        String openapi = openApiGenerator.generateYaml(colegioModel, "colegio-api");

        Map<String, String> files = flutterGenerator.generateFiles(colegioModel, openapi, "Colegio Móvil");
        assertNotNull(files);

        // Verify models with inheritance: Estudiante extends Persona, Profesor extends Persona
        assertTrue(files.containsKey("lib/data/models/persona.dart"));
        assertTrue(files.containsKey("lib/data/models/estudiante.dart"));
        assertTrue(files.containsKey("lib/data/models/profesor.dart"));
        assertTrue(files.containsKey("lib/data/models/curso.dart"));
        assertTrue(files.containsKey("lib/data/models/aula.dart"));
        assertTrue(files.containsKey("lib/data/models/nivel_educativo.dart"));

        String estudianteModel = files.get("lib/data/models/estudiante.dart");
        assertTrue(estudianteModel.contains("class Estudiante extends Persona"));
        assertTrue(estudianteModel.contains("NivelEducativo nivel"));
        assertTrue(estudianteModel.contains("codigoEstudiante"));

        String profesorModel = files.get("lib/data/models/profesor.dart");
        assertTrue(profesorModel.contains("class Profesor extends Persona"));

        // Verify Curso relations (profesorId, aulaId)
        String cursoModel = files.get("lib/data/models/curso.dart");
        assertTrue(cursoModel.contains("String? profesorId"));
        assertTrue(cursoModel.contains("String? aulaId"));
    }

    // =========================================================================
    // 3. SALUD MODEL
    // =========================================================================

    @Test
    void generatesFlutterAppForSaludModel() throws Exception {
        var saludModel = buildSaludModel();
        String openapi = openApiGenerator.generateYaml(saludModel, "salud-api");

        Map<String, String> files = flutterGenerator.generateFiles(saludModel, openapi, "Salud Móvil");
        assertNotNull(files);

        assertTrue(files.containsKey("lib/data/models/paciente.dart"));
        assertTrue(files.containsKey("lib/data/models/medico.dart"));
        assertTrue(files.containsKey("lib/data/models/cita.dart"));
        assertTrue(files.containsKey("lib/data/models/historia_clinica.dart"));
        assertTrue(files.containsKey("lib/data/models/tratamiento.dart"));
        assertTrue(files.containsKey("lib/data/models/estado_cita.dart"));

        String citaModel = files.get("lib/data/models/cita.dart");
        assertTrue(citaModel.contains("EstadoCita estado"));
        assertTrue(citaModel.contains("String? medicoId"));
        assertTrue(citaModel.contains("List<String> tratamientoIds"));
    }

    // =========================================================================
    // 4. ZIP ARCHIVE GENERATION AND PATH TRAVERSAL PROTECTION
    // =========================================================================

    @Test
    void generatesValidFlutterZipArchiveAndProtectsAgainstPathTraversal() throws Exception {
        var ventasModel = buildVentasModel();
        String openapi = openApiGenerator.generateYaml(ventasModel, "ventas-api");

        byte[] zipBytes = flutterGenerator.generateZip(ventasModel, openapi, "Ventas Móvil");
        assertNotNull(zipBytes);
        assertTrue(zipBytes.length > 5000);

        Map<String, String> extracted = new HashMap<>();
        try (ZipInputStream zipInput = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            for (var entry = zipInput.getNextEntry(); entry != null; entry = zipInput.getNextEntry()) {
                assertFalse(entry.getName().contains(".."), "Ninguna entrada ZIP debe contener path traversal");
                byte[] bytes = zipInput.readAllBytes();
                extracted.put(entry.getName(), new String(bytes, StandardCharsets.UTF_8));
            }
        }

        assertTrue(extracted.containsKey("pubspec.yaml"));
        assertTrue(extracted.containsKey("lib/main.dart"));
        assertTrue(extracted.containsKey("lib/data/models/pedido.dart"));
    }

    // =========================================================================
    // 5. FLUTTER ANALYZE AND FLUTTER TEST EXECUTION ON GENERATED APP
    // =========================================================================

    @Test
    void generatedFlutterAppPassesFlutterAnalyzeAndTestsWhenFlutterInstalled() throws Exception {
        if (!isFlutterInstalled()) {
            System.out.println("Flutter no está instalado en PATH; se omite la prueba de flutter analyze");
            return;
        }

        var ventasModel = buildVentasModel();
        String openapi = openApiGenerator.generateYaml(ventasModel, "ventas-api");

        Path tempDir = Files.createTempDirectory("flutter_gen_test_");
        try {
            flutterGenerator.writeToDirectory(ventasModel, openapi, "Ventas Móvil", tempDir);

            // 1. flutter pub get
            Process pubGet = new ProcessBuilder("flutter.bat", "pub", "get")
                .directory(tempDir.toFile())
                .redirectErrorStream(true)
                .start();
            String pubGetOutput = readProcessOutput(pubGet);
            int pubGetCode = pubGet.waitFor();
            assertEquals(0, pubGetCode, "flutter pub get debe completar con éxito:\n" + pubGetOutput);

            // 2. flutter analyze
            Process analyze = new ProcessBuilder("flutter.bat", "analyze", "--no-fatal-infos")
                .directory(tempDir.toFile())
                .redirectErrorStream(true)
                .start();
            String analyzeOutput = readProcessOutput(analyze);
            int analyzeCode = analyze.waitFor();
            assertEquals(0, analyzeCode, "flutter analyze debe pasar con 0 errores:\n" + analyzeOutput);

            // 3. flutter test
            Process testProcess = new ProcessBuilder("flutter.bat", "test")
                .directory(tempDir.toFile())
                .redirectErrorStream(true)
                .start();
            String testOutput = readProcessOutput(testProcess);
            int testCode = testProcess.waitFor();
            assertEquals(0, testCode, "flutter test debe pasar con 0 errores:\n" + testOutput);

        } finally {
            deleteDirectory(tempDir.toFile());
        }
    }

    @Test
    void generatesCanonicalFlutterAppInMobileModule() throws Exception {
        var ventasModel = buildVentasModel();
        String openapi = openApiGenerator.generateYaml(ventasModel, "ventas-api");
        boolean materialize = Boolean.getBoolean("materializeMobile");
        Path mobileModuleDir = materialize
            ? Path.of("..", "mobile-flutter").toAbsolutePath().normalize()
            : Files.createTempDirectory("mobile_materialized_");
        try {
            flutterGenerator.writeToDirectory(ventasModel, openapi, "Ventas Móvil", mobileModuleDir);
            assertTrue(Files.exists(mobileModuleDir.resolve("pubspec.yaml")), "pubspec.yaml debe existir");
            assertTrue(Files.exists(mobileModuleDir.resolve("lib/main.dart")), "main.dart debe existir");
            assertTrue(Files.exists(mobileModuleDir.resolve("lib/data/models/pedido.dart")), "pedido.dart debe existir");
            assertTrue(Files.exists(mobileModuleDir.resolve("lib/core/ai/local_ocr_service.dart")), "OCR local debe generarse");
        } finally {
            if (!materialize) deleteDirectory(mobileModuleDir.toFile());
        }
    }

    // =========================================================================
    // HELPERS FOR MODELS & TOOLS
    // =========================================================================

    private boolean isFlutterInstalled() {
        try {
            Process p = new ProcessBuilder("flutter.bat", "--version").start();
            return p.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private String readProcessOutput(Process process) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
        }
        return sb.toString();
    }

    private void deleteDirectory(java.io.File file) {
        if (file.isDirectory()) {
            var children = file.listFiles();
            if (children != null) {
                for (var child : children) {
                    deleteDirectory(child);
                }
            }
        }
        file.delete();
    }

    private DiagramDocument buildVentasModel() {
        var estadoEnum = new DiagramDocument.Enumeration(UUID.randomUUID(), "EstadoPedido", List.of(
            new DiagramDocument.EnumerationValue(UUID.randomUUID(), "NUEVO", 1),
            new DiagramDocument.EnumerationValue(UUID.randomUUID(), "PAGADO", 1),
            new DiagramDocument.EnumerationValue(UUID.randomUUID(), "CANCELADO", 1)
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

        var genEst = new DiagramDocument.Generalization(UUID.randomUUID(), persId, estId, 1);
        var genProf = new DiagramDocument.Generalization(UUID.randomUUID(), persId, profId, 1);

        return new DiagramDocument(
            UUID.randomUUID(), "Colegio", 1,
            List.of(persona, estudiante, profesor, aula, curso),
            List.of(nivelEnum),
            List.of(
                new DiagramDocument.Association(UUID.randomUUID(), profId, cursoId, "1", "0..*", "cursos", 1),
                new DiagramDocument.Association(UUID.randomUUID(), estId, cursoId, "0..*", "0..*", "cursos", null, null, "SOURCE", 1),
                new DiagramDocument.Association(UUID.randomUUID(), cursoId, aulaId, "1", "0..1", "aula", null, null, "SOURCE", 1)
            ),
            List.of(genEst, genProf)
        );
    }

    private DiagramDocument buildSaludModel() {
        var estadoCitaEnum = new DiagramDocument.Enumeration(UUID.randomUUID(), "EstadoCita", List.of(
            new DiagramDocument.EnumerationValue(UUID.randomUUID(), "PENDIENTE", 1),
            new DiagramDocument.EnumerationValue(UUID.randomUUID(), "CONFIRMADA", 1),
            new DiagramDocument.EnumerationValue(UUID.randomUUID(), "ATENDIDA", 1)
        ), new DiagramDocument.Position(0, 0), 1);

        UUID pacId = UUID.randomUUID();
        var paciente = new DiagramDocument.ClassElement(pacId, "Paciente", List.of(
            new DiagramDocument.Attribute(UUID.randomUUID(), "id", "UUID", true, true, true),
            new DiagramDocument.Attribute(UUID.randomUUID(), "nombre", "String", false, true, false),
            new DiagramDocument.Attribute(UUID.randomUUID(), "fechaNacimiento", "Date", false, true, false)
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
            new DiagramDocument.Attribute(UUID.randomUUID(), "fechaHora", "DateTime", false, true, false),
            new DiagramDocument.Attribute(UUID.randomUUID(), "motivo", "String", false, true, false),
            new DiagramDocument.Attribute(UUID.randomUUID(), "estado", "EstadoCita", false, true, false)
        ), new DiagramDocument.Position(0, 0), 1);

        UUID histId = UUID.randomUUID();
        var historia = new DiagramDocument.ClassElement(histId, "HistoriaClinica", List.of(
            new DiagramDocument.Attribute(UUID.randomUUID(), "id", "UUID", true, true, true),
            new DiagramDocument.Attribute(UUID.randomUUID(), "numeroHistorial", "String", false, true, true)
        ), new DiagramDocument.Position(0, 0), 1);

        UUID tratId = UUID.randomUUID();
        var tratamiento = new DiagramDocument.ClassElement(tratId, "Tratamiento", List.of(
            new DiagramDocument.Attribute(UUID.randomUUID(), "id", "UUID", true, true, true),
            new DiagramDocument.Attribute(UUID.randomUUID(), "nombre", "String", false, true, false)
        ), new DiagramDocument.Position(0, 0), 1);

        return new DiagramDocument(
            UUID.randomUUID(), "Salud", 1,
            List.of(paciente, medico, cita, historia, tratamiento),
            List.of(estadoCitaEnum),
            List.of(
                new DiagramDocument.Association(UUID.randomUUID(), medId, citaId, "1", "0..*", "citas", 1),
                new DiagramDocument.Association(UUID.randomUUID(), pacId, histId, "1", "1", "historia", null, null, "SOURCE", 1),
                new DiagramDocument.Association(UUID.randomUUID(), citaId, tratId, "0..*", "0..*", "tratamientos", null, null, "SOURCE", 1)
            ),
            List.of()
        );
    }
}
