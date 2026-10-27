package com.floworchestrator.controlcenter.model;

import com.floworchestrator.controlcenter.enums.ProcessExecutionStatus;

import java.util.List;

public record ProcessExecutionResponse(
        String id,
        String processTemplateId,
        String customerId,
        ProcessExecutionStatus status,
        String currentStep,
        String errorMessage,
        String userErrorTitle,
        String userErrorMessage,
        String userErrorActionLabel,
        String userErrorStatusType,
        String traceId,
        List<String> steps
) {
}
