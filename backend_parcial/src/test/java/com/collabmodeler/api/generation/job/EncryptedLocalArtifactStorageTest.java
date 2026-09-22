package com.collabmodeler.api.generation.job;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.assertj.core.api.Assertions.assertThat;

class EncryptedLocalArtifactStorageTest {
    @TempDir Path directory;
    @Test void encryptsAtRestAndRoundTripsBytes() throws Exception {
        var storage = new EncryptedLocalArtifactStorage(directory.toString(), "test-key");
        storage.put("diagram/job/random.zip.enc", "secret zip".getBytes(), "application/zip");
        assertThat(Files.readString(directory.resolve("diagram/job/random.zip.enc"), java.nio.charset.StandardCharsets.ISO_8859_1)).doesNotContain("secret zip");
        var stored = storage.get("diagram/job/random.zip.enc");
        assertThat(stored.contentType()).isEqualTo("application/zip");
        assertThat(new String(stored.contents())).isEqualTo("secret zip");
    }
}
