package com.floworchestrator.controlcenter.enums;

/**
 * Estatus del ciclo de vida de una ejecución de proceso.
 * Se persiste como el tipo PostgreSQL {@code execution_status}.
 */
public enum ProcessExecutionStatus {
    PENDING,
    IN_PROGRESS,
    PAUSED_FOR_HUMAN,
    COMPLETED,
    FAILED,
    CANCELLED;

    /**
     * Indica si la ejecución ya terminó y no admite más cambios.
     */
    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED || this == CANCELLED;
    }

    /**
     * Indica si la ejecución puede reintentarse (pausada o fallida).
     */
    public boolean isRetryable() {
        return this == PAUSED_FOR_HUMAN || this == FAILED;
    }
}
