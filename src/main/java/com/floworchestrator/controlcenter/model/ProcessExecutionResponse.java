package com.floworchestrator.controlcenter.model;

import java.util.List;
import java.util.UUID;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class ProcessExecutionResponse {
    private String id = UUID.randomUUID().toString();
    private String processTemplateId;
    private String customerId;
    private String status = "PENDING";
    private String currentStep;
    private String errorMessage;
    private List<String> steps;
}
