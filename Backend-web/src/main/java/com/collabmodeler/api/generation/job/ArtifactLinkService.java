package com.collabmodeler.api.generation.job;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class ArtifactLinkService {
    private final byte[] secret; private final Duration ttl;
    public ArtifactLinkService(@Value("${app.generation.download-secret:local-download-secret-change-me}") String secret,
                               @Value("${app.generation.download-url-minutes:5}") long minutes) {
        this.secret = secret.getBytes(StandardCharsets.UTF_8); this.ttl = Duration.ofMinutes(Math.max(1, minutes));
    }
    public SignedLink create(UUID diagramId, UUID jobId, String kind) {
        long expires = Instant.now().plus(ttl).getEpochSecond();
        String signature = sign(diagramId, jobId, kind, expires);
        return new SignedLink("/api/v1/diagrams/" + diagramId + "/generation-jobs/" + jobId + "/artifacts/" + kind
            + "?expires=" + expires + "&signature=" + signature, Instant.ofEpochSecond(expires));
    }
    public boolean valid(UUID diagramId, UUID jobId, String kind, long expires, String signature) {
        if (expires < Instant.now().getEpochSecond() || signature == null) return false;
        return MessageDigest.isEqual(sign(diagramId, jobId, kind, expires).getBytes(StandardCharsets.US_ASCII), signature.getBytes(StandardCharsets.US_ASCII));
    }
    private String sign(UUID diagramId, UUID jobId, String kind, long expires) {
        try { Mac mac = Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal((diagramId + ":" + jobId + ":" + kind + ":" + expires).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) { throw new IllegalStateException(e); }
    }
    public record SignedLink(String url, Instant expiresAt) {}
}
