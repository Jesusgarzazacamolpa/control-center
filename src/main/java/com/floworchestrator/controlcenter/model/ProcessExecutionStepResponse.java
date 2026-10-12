package com.floworchestrator.controlcenter.model;

import com.floworchestrator.controlcenter.enums.StepExecutionStatus;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
public class ProcessExecutionStepResponse {
    private String id;
    private int stepOrder;
    private String name;
    private StepExecutionStatus status;
    private int retryCount;
    private String errorMessage;
    private OffsetDateTime startedAt;
    private OffsetDateTime completedAt;
}