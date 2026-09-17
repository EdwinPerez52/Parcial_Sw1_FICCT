package com.collabmodeler.api.generation;

import com.collabmodeler.api.diagram.DiagramDocument;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipInputStream;
import javax.tools.ToolProvider;

import static org.junit.jupiter.api.Assertions.*;

class BackendGeneratorTest {
    @Test
    void createsFiveLayersAndTraceability() throws Exception {
        var id = new DiagramDocument.Attribute(UUID.randomUUID(), "id", "UUID", true, true, true);
        var name = new DiagramDocument.Attribute(UUID.randomUUID(), "nombre", "String", false, true, false);
        var model = new DiagramDocument(UUID.randomUUID(), "Ventas", 7,
            List.of(new DiagramDocument.ClassElement(UUID.randomUUID(), "Producto", List.of(id, name), new DiagramDocument.Position(0, 0), 1)), List.of());
        byte[] zip = new BackendGenerator(new ObjectMapper()).generate(model, "com.example", "ventas-api");
        StringBuilder names = new StringBuilder();
        try (ZipInputStream input = new ZipInputStream(new ByteArrayInputStream(zip))) {
            for (var entry = input.getNextEntry(); entry != null; entry = input.getNextEntry()) names.append(entry.getName()).append('\n');
        }
        assertTrue(names.toString().contains("model/Producto.java"));
        assertTrue(names.toString().contains("repository/ProductoRepository.java"));
        assertTrue(names.toString().contains("service/ProductoService.java"));
        assertTrue(names.toString().contains("dto/ProductoDto.java"));
        assertTrue(names.toString().contains("controller/ProductoController.java"));
        assertTrue(names.toString().contains("model-traceability.json"));
    }

    @Test
    void refusesClassesWithoutOnePrimaryKey() {
        var model = new DiagramDocument(UUID.randomUUID(), "Invalid", 0,
            List.of(new DiagramDocument.ClassElement(UUID.randomUUID(), "Thing", List.of(), new DiagramDocument.Position(0, 0), 1)), List.of());
        assertThrows(IllegalArgumentException.class, () -> new BackendGenerator(new ObjectMapper()).generate(model, "com.example", "api"));
    }

    @Test
    void generatedJavaSourcesCompile() throws Exception {
        var id = new DiagramDocument.Attribute(UUID.randomUUID(), "id", "UUID", true, true, true);
        var amount = new DiagramDocument.Attribute(UUID.randomUUID(), "total", "Decimal", false, true, false);
        var model = new DiagramDocument(UUID.randomUUID(), "Ventas", 1,
            List.of(new DiagramDocument.ClassElement(UUID.randomUUID(), "Pedido", List.of(id, amount), new DiagramDocument.Position(0, 0), 1)), List.of());
        byte[] archive = new BackendGenerator(new ObjectMapper()).generate(model, "com.example", "ventas-api");
        Path root = Files.createTempDirectory("generated-backend-");
        var sources = new ArrayList<String>();
        try (ZipInputStream input = new ZipInputStream(new ByteArrayInputStream(archive))) {
            for (var entry = input.getNextEntry(); entry != null; entry = input.getNextEntry()) {
                if (!entry.getName().endsWith(".java")) continue;
                Path target = root.resolve(entry.getName()); Files.createDirectories(target.getParent()); Files.copy(input, target); sources.add(target.toString());
            }
        }
        var arguments = new ArrayList<String>();
        arguments.add("-classpath"); arguments.add(System.getProperty("java.class.path")); arguments.add("-d"); arguments.add(root.resolve("classes").toString());
        Files.createDirectories(root.resolve("classes")); arguments.addAll(sources);
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null, arguments.toArray(String[]::new)));
    }
}
