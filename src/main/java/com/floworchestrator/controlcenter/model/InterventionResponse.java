package com.floworchestrator.controlcenter.model;

import com.floworchestrator.controlcenter.enums.InterventionStatus;

import java.time.OffsetDateTime;

/**
 * Representación de una intervención devuelta por la API.
 */
public record InterventionResponse(
        String id,
        String processId, // Identificador de la ejecución de proceso asociada.
        String stepName,
        int stepOrder,
        InterventionStatus status,
        String errorMessage,
        String userErrorTitle,
        String userErrorMessage,
        String userErrorActionLabel,
        String userErrorStatusType,
        String traceId,
        String assignedTo,
        String resolutionNotes,
        String suggestedAction,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
