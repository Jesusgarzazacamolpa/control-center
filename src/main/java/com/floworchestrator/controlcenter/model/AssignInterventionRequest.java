package com.floworchestrator.controlcenter.model;

import jakarta.validation.constraints.NotBlank;

public record AssignInterventionRequest(
        @NotBlank(message = "El responsable de la intervención es obligatorio.")
        String assignee
) {
}
