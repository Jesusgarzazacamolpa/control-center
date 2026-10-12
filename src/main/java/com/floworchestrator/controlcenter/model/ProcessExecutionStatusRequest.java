package com.floworchestrator.controlcenter.model;

import com.floworchestrator.controlcenter.enums.ProcessExecutionStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ProcessExecutionStatusRequest {
    @NotNull
    private ProcessExecutionStatus status;
    private String currentStep;
    private String errorMessage;
}