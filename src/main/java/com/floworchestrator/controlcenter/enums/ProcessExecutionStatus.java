package com.floworchestrator.controlcenter.enums;

public enum ProcessExecutionStatus {
    PENDING,
    IN_PROGRESS,
    PAUSED_FOR_HUMAN,
    COMPLETED,
    FAILED,
    CANCELLED;

    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED || this == CANCELLED;
    }

    public boolean isRetryable() {
        return this == PAUSED_FOR_HUMAN || this == FAILED;
    }
}
