package com.floworchestrator.controlcenter.model;

import com.floworchestrator.controlcenter.enums.StepExecutionStatus;
import jakarta.validation.constraints.NotNull;

/**
 * Solicitud para actualizar el estatus de un paso.
 */
public record StepExecutionStatusRequest(
        @NotNull(message = "El estatus del paso es obligatorio.")
        StepExecutionStatus status,

        String errorMessage,
        String userErrorTitle,
        String userErrorMessage,
        String userErrorActionLabel,
        String userErrorStatusType
) {

    public static StepExecutionStatusRequest of(StepExecutionStatus status) {
        return new StepExecutionStatusRequest(status, null, null, null, null, null);
    }
}
