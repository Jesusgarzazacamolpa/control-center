package com.floworchestrator.controlcenter.model;

import com.floworchestrator.controlcenter.enums.StepExecutionStatus;

import java.time.OffsetDateTime;

/**
 * Vista del estado de un paso dentro de una ejecución.
 */
public record ProcessExecutionStepResponse(
        String id,
        int stepOrder,
        String name,
        StepExecutionStatus status,
        int retryCount,
        String errorMessage,
        OffsetDateTime startedAt,
        OffsetDateTime completedAt
) {
}
