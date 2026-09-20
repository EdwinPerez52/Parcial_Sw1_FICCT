package com.collabmodeler.api.ai;

import com.collabmodeler.api.access.AccessService;
import com.collabmodeler.api.auth.AccountPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SpeechTranscriptionControllerTest {
    private static final byte[] WEBM = {0x1a, 0x45, (byte) 0xdf, (byte) 0xa3, 1, 2, 3, 4};

    @Test void verifiedEditorCanTranscribeWithoutChangingDiagram() throws Exception {
        var access = mock(AccessService.class); var service = mock(SpeechTranscriptionService.class);
        var principal = principal(true); var diagram = UUID.randomUUID();
        when(service.transcribe(WEBM, "audio/webm")).thenReturn("crea una clase Producto");
        var result = new SpeechTranscriptionController(access, service).transcribe(diagram,
            new MockMultipartFile("file", "voz.webm", "audio/webm", WEBM), principal);
        assertEquals("crea una clase Producto", result.text());
        verify(access).requireEditor(diagram, "account:" + ((AccountPrincipal) principal.getPrincipal()).accountId());
        verify(service).transcribe(WEBM, "audio/webm");
    }

    @Test void readerIsRejectedBeforeAudioIsProcessed() {
        var access = mock(AccessService.class); var service = mock(SpeechTranscriptionService.class);
        var principal = principal(true); var diagram = UUID.randomUUID();
        doThrow(new AccessDeniedException("Editor requerido")).when(access).requireEditor(diagram, "account:" + ((AccountPrincipal) principal.getPrincipal()).accountId());
        assertThrows(AccessDeniedException.class, () -> new SpeechTranscriptionController(access, service).transcribe(diagram,
            new MockMultipartFile("file", "voz.webm", "audio/webm", WEBM), principal));
        verifyNoInteractions(service);
    }

    @Test void rejectsSpoofedAndOversizedAudio() {
        var service = mock(SpeechTranscriptionService.class);
        var controller = new SpeechTranscriptionController(mock(AccessService.class), service);
        assertThrows(IllegalArgumentException.class, () -> controller.transcribe(UUID.randomUUID(),
            new MockMultipartFile("file", "voz.webm", "audio/webm", "invalid".getBytes()), principal(true)));
        assertThrows(IllegalArgumentException.class, () -> controller.transcribe(UUID.randomUUID(),
            new MockMultipartFile("file", "voz.webm", "audio/webm", new byte[5_000_001]), principal(true)));
        verifyNoInteractions(service);
    }

    private UsernamePasswordAuthenticationToken principal(boolean verified) {
        UUID account = UUID.randomUUID();
        return UsernamePasswordAuthenticationToken.authenticated(
            new AccountPrincipal(account, "editor@example.com", "Editor", verified, false), null, List.of());
    }
}
