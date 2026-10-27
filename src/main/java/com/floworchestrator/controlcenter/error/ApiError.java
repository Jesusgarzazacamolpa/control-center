package com.floworchestrator.controlcenter.error;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Contrato de error único para las respuestas 4xx/5xx. El mensaje es apto para
 * mostrarse en la UI; el detalle técnico solo se registra en los logs.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(
        String flowCode,
        String message,
        String traceId,
        OffsetDateTime timestamp,
        String path,
        List<FieldViolation> details
) {

    public record FieldViolation(String field, String message) {
    }
}
