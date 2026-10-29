package com.floworchestrator.controlcenter.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.OffsetDateTime;

/**
 * DTO que representa un evento de ejecución de flujo recibido vía SSE desde el Orchestrator.
 * {@code eventType} determina el cambio de estado aplicado (STEP_*, FLOW_*); los campos
 * desconocidos del emisor se ignoran.
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
        String userErrorTitle,
        String userErrorMessage,
        String userErrorActionLabel,
        String userErrorStatusType,
        String traceId,
        OffsetDateTime timestamp
) {
}
