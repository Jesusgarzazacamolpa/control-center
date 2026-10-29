package com.floworchestrator.controlcenter.model;

import jakarta.validation.constraints.NotBlank;

/**
 * Solicitud para asignar una intervención a un responsable.
 */
public record AssignInterventionRequest(
        @NotBlank(message = "El responsable de la intervención es obligatorio.")
        String assignee
) {
}
