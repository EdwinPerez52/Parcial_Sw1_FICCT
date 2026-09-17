package com.collabmodeler.api.diagram;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.UUID;

public record DiagramOperationRequest(
    @NotNull UUID operationId,
    @PositiveOrZero long baseRevision,
    Long expectedElementVersion,
    @NotBlank String type,
    @NotNull JsonNode payload
) {}
