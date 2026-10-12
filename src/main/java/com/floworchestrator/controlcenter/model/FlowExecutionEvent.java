package com.floworchestrator.controlcenter.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.OffsetDateTime;

/**
 * DTO que representa un evento de ejecución de flujo recibido vía SSE desde el Orchestrator.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record FlowExecutionEvent(
        String executionId,
        String flowId,
        String eventType,
        Integer stepOrder,
        String stepName,
        String toolName,
        String server,
        String status,
        String errorMessage,
        OffsetDateTime timestamp
) {
}
