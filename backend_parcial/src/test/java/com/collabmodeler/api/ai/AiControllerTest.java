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
}
