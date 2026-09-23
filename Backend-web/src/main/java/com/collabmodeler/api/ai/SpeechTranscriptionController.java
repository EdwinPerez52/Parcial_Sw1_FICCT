package com.collabmodeler.api.ai;

import com.collabmodeler.api.access.AccessController;
import com.collabmodeler.api.access.AccessService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.security.Principal;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/diagrams/{diagramId}/assistant")
public class SpeechTranscriptionController {
    private static final int MAX_AUDIO_BYTES = 5_000_000;
    private final AccessService access;
    private final SpeechTranscriptionService transcription;

    public SpeechTranscriptionController(AccessService access, SpeechTranscriptionService transcription) {
        this.access = access; this.transcription = transcription;
    }

    public record Transcript(String text) {}

    @PostMapping(value = "/transcriptions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    Transcript transcribe(@PathVariable UUID diagramId, @RequestPart("file") MultipartFile file, Principal principal) throws IOException {
        AccessController.requireVerified(principal);
        access.requireEditor(diagramId, AccessController.subject(principal));
        if (file.isEmpty() || file.getSize() > MAX_AUDIO_BYTES) throw new IllegalArgumentException("El audio debe pesar entre 1 byte y 5 MB");
        byte[] bytes = file.getBytes();
        String mime = detectMime(bytes);
        if (!mime.equals(file.getContentType() == null ? "" : file.getContentType().split(";", 2)[0]))
            throw new IllegalArgumentException("El MIME declarado no coincide con el audio");
        return new Transcript(transcription.transcribe(bytes, mime));
    }

    static String detectMime(byte[] bytes) {
        if (bytes.length >= 4 && bytes[0] == 0x1a && bytes[1] == 0x45 && bytes[2] == (byte) 0xdf && bytes[3] == (byte) 0xa3) return "audio/webm";
        if (bytes.length >= 4 && bytes[0] == 'O' && bytes[1] == 'g' && bytes[2] == 'g' && bytes[3] == 'S') return "audio/ogg";
        if (bytes.length >= 12 && bytes[4] == 'f' && bytes[5] == 't' && bytes[6] == 'y' && bytes[7] == 'p') return "audio/mp4";
        throw new IllegalArgumentException("Formato de audio no admitido");
    }
}
