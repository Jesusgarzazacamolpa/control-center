package com.floworchestrator.controlcenter.model;

import com.floworchestrator.controlcenter.enums.StepExecutionStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class StepExecutionStatusRequest {
    @NotNull
    private StepExecutionStatus status;
    private String errorMessage;
}