package com.collabmodeler.api.ai;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import javax.imageio.ImageIO;

/** Inspects bytes rather than trusting the multipart Content-Type. */
final class ImageInspection {
    static final int MAX_BYTES = 10_000_000;
    static final int MAX_SIDE = 8_192;
    static final long MAX_PIXELS = 24_000_000;

    record Result(String mime, int width, int height) {}

    static Result inspect(byte[] bytes) throws IOException {
        if (bytes.length == 0 || bytes.length > MAX_BYTES) throw new IllegalArgumentException("La imagen debe pesar entre 1 byte y 10 MB");
        String mime;
        int width;
        int height;
        if (starts(bytes, 0, new byte[]{(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a})) {
            mime = "image/png";
            if (bytes.length < 24 || !starts(bytes, 12, "IHDR".getBytes(java.nio.charset.StandardCharsets.US_ASCII))) throw new IllegalArgumentException("PNG inválido");
            width = be32(bytes, 16); height = be32(bytes, 20);
        } else if (starts(bytes, 0, new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff})) {
            mime = "image/jpeg";
            try (var stream = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
                var readers = ImageIO.getImageReaders(stream);
                if (!readers.hasNext()) throw new IllegalArgumentException("JPEG inválido");
                var reader = readers.next();
                try { reader.setInput(stream); width = reader.getWidth(0); height = reader.getHeight(0); }
                finally { reader.dispose(); }
            }
        } else if (starts(bytes, 0, "RIFF".getBytes()) && starts(bytes, 8, "WEBP".getBytes()) && bytes.length >= 30) {
            mime = "image/webp";
            String chunk = new String(bytes, 12, 4, java.nio.charset.StandardCharsets.US_ASCII);
            if ("VP8X".equals(chunk)) { width = le24(bytes, 24) + 1; height = le24(bytes, 27) + 1; }
            else if ("VP8L".equals(chunk) && (bytes[20] & 0xff) == 0x2f) {
                width = 1 + ((bytes[21] & 0xff) | ((bytes[22] & 0x3f) << 8));
                height = 1 + (((bytes[22] & 0xc0) >> 6) | ((bytes[23] & 0xff) << 2) | ((bytes[24] & 0x0f) << 10));
            } else if ("VP8 ".equals(chunk) && starts(bytes, 23, new byte[]{(byte) 0x9d, 0x01, 0x2a})) {
                width = le16(bytes, 26) & 0x3fff; height = le16(bytes, 28) & 0x3fff;
            } else throw new IllegalArgumentException("WebP inválido");
        } else throw new IllegalArgumentException("Formato de imagen no admitido");
        if (width < 1 || height < 1 || width > MAX_SIDE || height > MAX_SIDE || (long) width * height > MAX_PIXELS)
            throw new IllegalArgumentException("Dimensiones de imagen fuera del límite permitido");
        return new Result(mime, width, height);
    }

    private static boolean starts(byte[] bytes, int offset, byte[] prefix) {
        if (offset + prefix.length > bytes.length) return false;
        for (int i = 0; i < prefix.length; i++) if (bytes[offset + i] != prefix[i]) return false;
        return true;
    }
    private static int be32(byte[] b, int o) { return ((b[o] & 255) << 24) | ((b[o + 1] & 255) << 16) | ((b[o + 2] & 255) << 8) | (b[o + 3] & 255); }
    private static int le16(byte[] b, int o) { return (b[o] & 255) | ((b[o + 1] & 255) << 8); }
    private static int le24(byte[] b, int o) { return le16(b, o) | ((b[o + 2] & 255) << 16); }
}
