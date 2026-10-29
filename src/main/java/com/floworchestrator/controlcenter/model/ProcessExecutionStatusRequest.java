package com.floworchestrator.controlcenter.model;

import com.floworchestrator.controlcenter.enums.ProcessExecutionStatus;
import jakarta.validation.constraints.NotNull;

/**
 * Solicitud para actualizar el estatus de una ejecución.
 */
public record ProcessExecutionStatusRequest(
        @NotNull(message = "El estatus del proceso es obligatorio.")
        ProcessExecutionStatus status,

        String currentStep,
        String errorMessage,
        String userErrorTitle,
        String userErrorMessage,
        String userErrorActionLabel,
        String userErrorStatusType
) {

    /**
     * Atajo para cambiar solo el estatus y limpiar los mensajes de error.
     */
    public static ProcessExecutionStatusRequest of(ProcessExecutionStatus status) {
        return new ProcessExecutionStatusRequest(status, null, null, null, null, null, null);
    }
}
