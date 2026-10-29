package com.floworchestrator.controlcenter.client;

/**
 * Datos necesarios para iniciar un flujo en el Orchestrator.
 * El {@code flowId} es el nombre de la plantilla de proceso que usa el Orchestrator
 * para resolver la definición del flujo.
 */
public record FlowStartCommand(
        String executionId,
        String flowId,
        String customerId,
        String vehicleId,
        String salesAgentId
) {
}
