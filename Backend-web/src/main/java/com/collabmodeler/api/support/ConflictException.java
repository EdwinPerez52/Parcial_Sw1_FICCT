package com.collabmodeler.api.support;

import java.util.UUID;

public class ConflictException extends RuntimeException {
    private final String code;
    private final Long currentRevision;
    private final UUID elementId;
    private final Long expectedElementVersion;
    private final Long actualElementVersion;

    public ConflictException(String message) { this("CONFLICT", message, null, null, null, null); }

    public ConflictException(String code, String message, Long currentRevision, UUID elementId, Long actualElementVersion) {
        this(code, message, currentRevision, elementId, null, actualElementVersion);
    }

    public ConflictException(String code, String message, Long currentRevision, UUID elementId,
                             Long expectedElementVersion, Long actualElementVersion) {
        super(message);
        this.code = code;
        this.currentRevision = currentRevision;
        this.elementId = elementId;
        this.expectedElementVersion = expectedElementVersion;
        this.actualElementVersion = actualElementVersion;
    }

    public String getCode() { return code; }
    public Long getCurrentRevision() { return currentRevision; }
    public UUID getElementId() { return elementId; }
    public Long getExpectedElementVersion() { return expectedElementVersion; }
    public Long getActualElementVersion() { return actualElementVersion; }
}
