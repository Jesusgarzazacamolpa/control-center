package com.floworchestrator.controlcenter.model;

/**
 * Datos para registrar una intervención humana.
 * Un {@code stepOrder} nulo se persiste como 0.
 */
public record CreateInterventionRequest(
        String stepName,
        Integer stepOrder,
        String errorMessage,
        String userErrorTitle,
        String userErrorMessage,
        String userErrorActionLabel,
        String userErrorStatusType
) {
}
