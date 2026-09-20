package com.collabmodeler.api.ai;
import com.collabmodeler.api.access.AccessController;
import com.collabmodeler.api.access.AccessService;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.security.Principal;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class AiController {
    private final AiService ai; private final AccessService access;
    public AiController(AiService ai, AccessService access) { this.ai = ai; this.access = access; }
    @PostMapping(value = "/ai/image-preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    JsonNode image(@RequestPart("file") MultipartFile file, Principal principal) throws IOException {
        AccessController.requireVerified(principal);
        return inspect(file);
    }

    @PostMapping(value = "/diagrams/{diagramId}/image-preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    JsonNode diagramImage(@PathVariable UUID diagramId, @RequestPart("file") MultipartFile file, Principal principal) throws IOException {
        AccessController.requireVerified(principal);
        access.requireEditor(diagramId, AccessController.subject(principal));
        return inspect(file);
    }

    private JsonNode inspect(MultipartFile file) throws IOException {
        if (file.isEmpty() || file.getSize() > ImageInspection.MAX_BYTES) throw new IllegalArgumentException("La imagen debe pesar entre 1 byte y 10 MB");
        byte[] bytes = file.getBytes();
        ImageInspection.Result inspected = ImageInspection.inspect(bytes);
        if (!inspected.mime().equals(file.getContentType())) throw new IllegalArgumentException("El MIME declarado no coincide con el contenido de la imagen");
        return ai.analyzeImage(bytes, inspected.mime());
    }
}
