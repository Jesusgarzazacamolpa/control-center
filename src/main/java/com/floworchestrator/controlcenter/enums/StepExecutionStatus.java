package com.floworchestrator.controlcenter.enums;

/**
 * Estatus de un paso dentro de una ejecución.
 * Se persiste como el tipo PostgreSQL {@code step_status}.
 */
public enum StepExecutionStatus {
    PENDING,
    RUNNING,
    REQUIRES_INTERVENTION,
    COMPLETED,
    FAILED
}