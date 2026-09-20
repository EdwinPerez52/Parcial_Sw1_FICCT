package com.collabmodeler.api.generation;

import java.util.UUID;

public class ModelValidationException extends IllegalArgumentException {
    private final UUID elementId;
    private final String code;

    public ModelValidationException(UUID elementId, String code, String message) {
        super(String.format("[%s] %s: %s", elementId != null ? elementId : "MODEL", code, message));
        this.elementId = elementId;
        this.code = code;
    }

    public UUID getElementId() {
        return elementId;
    }

    public String getCode() {
        return code;
    }
}
