package com.collabmodeler.api.generation.job;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.Arrays;

@Component
@Profile("!prod")
public class EncryptedLocalArtifactStorage implements ArtifactStorage {
    private final Path root;
    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public EncryptedLocalArtifactStorage(@Value("${app.generation.local-storage-path:./var/artifacts}") String path,
                                         @Value("${app.generation.encryption-key:local-generation-key-change-me}") String secret) {
        root = Paths.get(path).toAbsolutePath().normalize();
        try { key = new SecretKeySpec(MessageDigest.getInstance("SHA-256").digest(secret.getBytes(StandardCharsets.UTF_8)), "AES"); }
        catch (GeneralSecurityException e) { throw new IllegalStateException(e); }
    }

    @Override public void put(String objectKey, byte[] contents, String contentType) {
        try {
            Path target = resolve(objectKey); Files.createDirectories(target.getParent());
            byte[] iv = new byte[12]; random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
            byte[] encrypted = cipher.doFinal(contents);
            byte[] media = contentType.getBytes(StandardCharsets.UTF_8);
            byte[] payload = new byte[1 + media.length + iv.length + encrypted.length]; payload[0] = (byte) media.length;
            System.arraycopy(media, 0, payload, 1, media.length); System.arraycopy(iv, 0, payload, 1 + media.length, iv.length);
            System.arraycopy(encrypted, 0, payload, 1 + media.length + iv.length, encrypted.length);
            Files.write(target, payload, StandardOpenOption.CREATE_NEW);
        } catch (Exception e) { throw new IllegalStateException("No se pudo guardar el artefacto", e); }
    }

    @Override public StoredArtifact get(String objectKey) {
        try {
            byte[] payload = Files.readAllBytes(resolve(objectKey)); int mediaLength = Byte.toUnsignedInt(payload[0]);
            String media = new String(payload, 1, mediaLength, StandardCharsets.UTF_8);
            byte[] iv = Arrays.copyOfRange(payload, 1 + mediaLength, 13 + mediaLength);
            byte[] encrypted = Arrays.copyOfRange(payload, 13 + mediaLength, payload.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, iv));
            return new StoredArtifact(cipher.doFinal(encrypted), media);
        } catch (NoSuchFileException e) { throw new com.collabmodeler.api.support.NotFoundException("El artefacto expiró o no existe"); }
        catch (Exception e) { throw new IllegalStateException("No se pudo leer el artefacto", e); }
    }

    @Override public void delete(String objectKey) { try { Files.deleteIfExists(resolve(objectKey)); } catch (Exception ignored) {} }
    private Path resolve(String objectKey) {
        Path value = root.resolve(objectKey).normalize();
        if (!value.startsWith(root)) throw new SecurityException("Clave de objeto inválida");
        return value;
    }
}
