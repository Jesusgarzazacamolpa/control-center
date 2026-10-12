package com.floworchestrator.controlcenter.model;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ProcessExecutionRequest {
    @NotBlank(message = "processTemplateId is required")
    private String processTemplateId;

    @NotBlank(message = "customerId is required")
    private String customerId;

    private String vehicleId;

    private String salesAgentId;
}
