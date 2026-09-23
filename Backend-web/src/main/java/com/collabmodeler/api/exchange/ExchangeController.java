package com.collabmodeler.api.exchange;

import com.collabmodeler.api.access.AccessController;
import com.collabmodeler.api.access.AccessService;
import com.collabmodeler.api.diagram.DiagramService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/diagrams")
public class ExchangeController {
    private final XmiService xmi;
    private final DiagramService diagrams;
    private final AccessService access;
    public ExchangeController(XmiService xmi, DiagramService diagrams, AccessService access) {
        this.xmi = xmi; this.diagrams = diagrams; this.access = access;
    }

    @GetMapping(value = "/{id}/xmi", produces = MediaType.APPLICATION_XML_VALUE)
    ResponseEntity<byte[]> export(@PathVariable UUID id, Principal principal) {
        access.requireMember(id, AccessController.subject(principal));
        var diagram = diagrams.get(id);
        byte[] body = xmi.exportXmi(diagram).getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(diagram.name() + ".xmi").build().toString())
            .contentType(MediaType.APPLICATION_XML).body(body);
    }

    @PostMapping(value = "/xmi/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    XmiService.ImportResult preview(@RequestPart("file") MultipartFile file) throws IOException {
        return parse(file);
    }

    @PostMapping(value = "/{id}/xmi/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    XmiService.ImportResult previewForDiagram(@PathVariable UUID id, @RequestPart("file") MultipartFile file,
                                              Principal principal) throws IOException {
        AccessController.requireVerified(principal);
        access.requireEditor(id, AccessController.subject(principal));
        return parse(file);
    }

    private XmiService.ImportResult parse(MultipartFile file) throws IOException {
        if (file.isEmpty()) throw new IllegalArgumentException("Selecciona un archivo XMI no vacío");
        if (file.getSize() > XmiService.MAX_BYTES) throw new IllegalArgumentException("El XMI supera el límite de 5 MB");
        String originalName = file.getOriginalFilename();
        String fallback = originalName == null ? "Modelo_importado" : originalName.replaceFirst("(?i)\\.(xmi|xml)$", "");
        return xmi.importXmi(file.getBytes(), fallback.isBlank() ? "Modelo_importado" : fallback);
    }
}
