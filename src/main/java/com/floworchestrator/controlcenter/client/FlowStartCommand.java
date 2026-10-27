package com.floworchestrator.controlcenter.client;

/**
 * Datos necesarios para iniciar un flujo en el Orchestrator.
 */
public record FlowStartCommand(
        String executionId,
        String flowId,
        String customerId,
        String vehicleId,
        String salesAgentId
) {
}
