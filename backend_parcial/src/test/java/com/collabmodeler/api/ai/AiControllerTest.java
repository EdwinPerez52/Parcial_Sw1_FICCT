package com.collabmodeler.api.ai;

import com.collabmodeler.api.access.AccessService;
import com.collabmodeler.api.auth.AccountPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class AiControllerTest {
    @Test void rejectsClaimedPngWithDifferentBytesBeforeVision() {
        var ai = mock(AiService.class); var controller = new AiController(ai, mock(AccessService.class));
        var account = UUID.randomUUID();
        var principal = UsernamePasswordAuthenticationToken.authenticated(
            new AccountPrincipal(account, "editor@example.com", "Editor", true, false), null, List.of());
        var file = new MockMultipartFile("file", "photo.png", "image/png", "not an image".getBytes());
        assertThrows(IllegalArgumentException.class, () -> controller.image(file, principal));
        verifyNoInteractions(ai);
    }

    @Test void readerCannotSendImageToVisionAdapter() {
        var ai = mock(AiService.class); var access = mock(AccessService.class);
        var account = UUID.randomUUID(); var diagram = UUID.randomUUID();
        var principal = UsernamePasswordAuthenticationToken.authenticated(
            new AccountPrincipal(account, "reader@example.com", "Reader", true, false), null, List.of());
        doThrow(new AccessDeniedException("Se requiere rol de editor")).when(access).requireEditor(diagram, "account:" + account);
        var controller = new AiController(ai, access);
        assertThrows(AccessDeniedException.class, () -> controller.diagramImage(diagram,
            new MockMultipartFile("file", "photo.png", "image/png", new byte[]{1}), principal));
        verifyNoInteractions(ai);
    }

    @Test
    void mobileAnalyzeProcessesValidImageAndReturnsProposal() throws Exception {
        var ai = mock(AiService.class);
        var controller = new AiController(ai, mock(AccessService.class));
        var account = UUID.randomUUID();
        var principal = UsernamePasswordAuthenticationToken.authenticated(
            new AccountPrincipal(account, "user@example.com", "User", true, false), null, List.of());

        byte[] validPng = new byte[]{
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, // signature
            0x00, 0x00, 0x00, 0x0D, // IHDR chunk length (13)
            'I', 'H', 'D', 'R',     // chunk type
            0x00, 0x00, 0x00, 0x64, // width = 100
            0x00, 0x00, 0x00, 0x64  // height = 100
        };
        var file = new MockMultipartFile("file", "receipt.png", "image/png", validPng);
        when(ai.analyzeMobileImage(any(), eq("image/png"), eq("analizar factura")))
            .thenReturn(java.util.Map.of("action", "create", "entity", "Factura", "confidence", 0.95));

        var result = controller.mobileAnalyze(file, "analizar factura", principal);
        org.junit.jupiter.api.Assertions.assertNotNull(result);
        org.junit.jupiter.api.Assertions.assertEquals("Factura", result.get("entity"));
        verify(ai).analyzeMobileImage(any(), eq("image/png"), eq("analizar factura"));
    }

    @Test
    void mobileAnalyzeProcessesPromptWhenNoImage() throws Exception {
        var ai = mock(AiService.class);
        var controller = new AiController(ai, mock(AccessService.class));
        var account = UUID.randomUUID();
        var principal = UsernamePasswordAuthenticationToken.authenticated(
            new AccountPrincipal(account, "user@example.com", "User", true, false), null, List.of());

        when(ai.analyzeMobilePrompt("crear cliente Ana email ana@test.com"))
            .thenReturn(java.util.Map.of("action", "create", "entity", "Cliente"));

        var result = controller.mobileAnalyze(null, "crear cliente Ana email ana@test.com", principal);
        org.junit.jupiter.api.Assertions.assertEquals("Cliente", result.get("entity"));
        verify(ai).analyzeMobilePrompt("crear cliente Ana email ana@test.com");
    }

    @Test
    void mobileAnalyzeRejectsWhenNeitherImageNorPromptProvided() {
        var ai = mock(AiService.class);
        var controller = new AiController(ai, mock(AccessService.class));
        var account = UUID.randomUUID();
        var principal = UsernamePasswordAuthenticationToken.authenticated(
            new AccountPrincipal(account, "user@example.com", "User", true, false), null, List.of());

        assertThrows(IllegalArgumentException.class, () -> controller.mobileAnalyze(null, null, principal));
        verifyNoInteractions(ai);
    }
}
