package com.collabmodeler.api.exchange;

import com.collabmodeler.api.access.AccessService;
import com.collabmodeler.api.auth.AccountPrincipal;
import com.collabmodeler.api.diagram.DiagramService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class ExchangeControllerTest {
    @Test
    void previewForDiagramRequiresEditorAndReturnsStructuredWarnings() throws Exception {
        AccessService access = mock(AccessService.class);
        UUID diagramId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        String subject = "account:" + accountId;
        var principal = UsernamePasswordAuthenticationToken.authenticated(
            new AccountPrincipal(accountId, "ana@example.com", "Ana", true, false), null, List.of());
        String xml = """
            <xmi:XMI xmi:version="2.1" xmlns:xmi="http://schema.omg.org/spec/XMI/2.1" xmlns:uml="http://schema.omg.org/spec/UML/2.1">
              <uml:Model xmi:id="m" name="M"><packagedElement xmi:type="uml:Component" xmi:id="unsupported" name="C"/></uml:Model>
            </xmi:XMI>
            """;
        var file = new MockMultipartFile("file", "model.xmi", "application/xml", xml.getBytes(StandardCharsets.UTF_8));
        var controller = new ExchangeController(new XmiService(), mock(DiagramService.class), access);

        var result = controller.previewForDiagram(diagramId, file, principal);

        verify(access).requireEditor(diagramId, subject);
        assertEquals("UNSUPPORTED_ELEMENT", result.warnings().getFirst().code());
    }

    @Test
    void readerCannotPreviewAnImportForConfirmation() {
        AccessService access = mock(AccessService.class);
        UUID diagramId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        var principal = UsernamePasswordAuthenticationToken.authenticated(
            new AccountPrincipal(accountId, "reader@example.com", "Reader", true, false), null, List.of());
        doThrow(new AccessDeniedException("Se requiere rol de editor"))
            .when(access).requireEditor(diagramId, "account:" + accountId);
        var file = new MockMultipartFile("file", "model.xmi", "application/xml", "x".getBytes(StandardCharsets.UTF_8));
        var controller = new ExchangeController(new XmiService(), mock(DiagramService.class), access);

        assertThrows(AccessDeniedException.class, () -> controller.previewForDiagram(diagramId, file, principal));
    }
}
