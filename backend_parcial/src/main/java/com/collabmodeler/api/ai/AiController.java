package com.collabmodeler.api.ai;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/ai")
public class AiController {
    private static final Set<String> IMAGES = Set.of("image/jpeg", "image/png", "image/webp");
    private final AiService ai; public AiController(AiService ai) { this.ai = ai; }
    public record CommandRequest(@NotBlank String command, @NotNull JsonNode diagram) {}
    @PostMapping("/command") JsonNode command(@Valid @RequestBody CommandRequest request) { return ai.interpret(request.command(), request.diagram()); }
    @PostMapping(value = "/image-preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    JsonNode image(@RequestPart("file") MultipartFile file) throws IOException {
        if (file.isEmpty() || file.getSize() > 10_000_000) throw new IllegalArgumentException("La imagen debe pesar entre 1 byte y 10 MB");
        if (!IMAGES.contains(file.getContentType())) throw new IllegalArgumentException("Formato de imagen no admitido");
        return ai.analyzeImage(file.getBytes(), file.getContentType());
    }
}

