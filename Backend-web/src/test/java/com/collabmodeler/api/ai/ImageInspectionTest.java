package com.collabmodeler.api.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ImageInspectionTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test void detectsRealPngAndDimensions() throws Exception {
        byte[] bytes = new byte[24];
        byte[] header = {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};
        System.arraycopy(header, 0, bytes, 0, header.length);
        System.arraycopy("IHDR".getBytes(), 0, bytes, 12, 4);
        bytes[19] = 10; bytes[23] = 20;
        var result = ImageInspection.inspect(bytes);
        assertEquals("image/png", result.mime()); assertEquals(10, result.width()); assertEquals(20, result.height());
        bytes[18] = 0x20; bytes[19] = 0x01;
        assertThrows(IllegalArgumentException.class, () -> ImageInspection.inspect(bytes));
    }

    @Test void rejectsSpoofedAndEmptyContent() {
        assertThrows(IllegalArgumentException.class, () -> ImageInspection.inspect("not an image".getBytes()));
        assertThrows(IllegalArgumentException.class, () -> ImageInspection.inspect(new byte[0]));
    }

    @Test void rejectsUnusableVisionProposal() throws Exception {
        var valid = mapper.readTree(""" 
            {"classes":[{"name":"Product","attributes":[{"name":"id","type":"UUID","primaryKey":true,"required":true,"unique":true}]}],
             "associations":[],"warnings":[],"confidence":0.8}
            """);
        assertSame(valid, ImageProposalValidator.validate(valid));
        ((com.fasterxml.jackson.databind.node.ObjectNode) valid).put("confidence", 1.5);
        assertThrows(IllegalArgumentException.class, () -> ImageProposalValidator.validate(valid));
    }
}
