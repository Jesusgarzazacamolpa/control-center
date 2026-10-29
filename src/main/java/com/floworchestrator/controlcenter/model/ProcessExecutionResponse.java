package com.floworchestrator.controlcenter.model;

import com.floworchestrator.controlcenter.enums.ProcessExecutionStatus;

import java.util.List;

/**
 * Vista de una ejecución de proceso expuesta por la API.
 */
public record ProcessExecutionResponse(
        String id,
        String processTemplateId,
        String customerId, // Se extrae del globalContext jsonb de la ejecución.
        ProcessExecutionStatus status,
        String currentStep,
        String errorMessage,
        String userErrorTitle,
        String userErrorMessage,
        String userErrorActionLabel,
        String userErrorStatusType,
        String traceId,
        List<String> steps // Nombres de los pasos de la plantilla, en orden.
) {
}
