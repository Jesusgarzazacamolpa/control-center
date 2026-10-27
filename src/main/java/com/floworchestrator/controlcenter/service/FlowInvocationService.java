package com.floworchestrator.controlcenter.service;

import com.floworchestrator.controlcenter.client.FlowStartCommand;
import com.floworchestrator.controlcenter.client.OrchestratorClient;
import com.floworchestrator.controlcenter.entity.ProcessTemplateEntity;
import com.floworchestrator.controlcenter.enums.ProcessExecutionStatus;
import com.floworchestrator.controlcenter.exception.InvalidProcessStateException;
import com.floworchestrator.controlcenter.model.ProcessExecutionRequest;
import com.floworchestrator.controlcenter.model.ProcessExecutionResponse;
import com.floworchestrator.controlcenter.model.ProcessExecutionStatusRequest;
import com.floworchestrator.controlcenter.repository.ProcessTemplateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Caso de uso que coordina la creación de la ejecución en el Control Center con
 * el arranque, reanudación y reintento del flujo en el Orchestrator.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FlowInvocationService {

    private static final String DEFAULT_FLOW_NAME = "Compra de vehiculo";

    private final ProcessService processService;
    private final ProcessTemplateRepository processTemplateRepository;
    private final OrchestratorClient orchestratorClient;
    private final FlowEventSseConsumer flowEventSseConsumer;

    /**
     * Crea la ejecución en el Control Center y dispara el flujo en el Orchestrator.
     * El estatus de cada paso se reporta mediante eventos SSE.
     */
    public ProcessExecutionResponse startProcess(ProcessExecutionRequest request) {
        flowEventSseConsumer.ensureStarted();

        ProcessExecutionResponse execution = processService.createExecution(request);
        flowEventSseConsumer.trackExecution(execution.id());

        UUID executionId = UUID.fromString(execution.id());
        startFlow(executionId, resolveTemplateName(request.processTemplateId()), request);
        return execution;
    }

    /**
     * Reintenta una ejecución pausada o fallida. Si el Orchestrator no tiene
     * estado previo, reinicia el flujo desde el principio.
     */
    public ProcessExecutionResponse retryProcess(UUID executionId, String userInput) {
        ProcessExecutionResponse current = processService.getExecution(executionId);
        if (!current.status().isRetryable()) {
            throw new InvalidProcessStateException(executionId, current.status());
        }

        flowEventSseConsumer.reRegisterExecution(executionId.toString());
        if (!resumeFlow(executionId, userInput)) {
            restartFlow(executionId);
        }
        return processService.getExecution(executionId);
    }

    /**
     * Notifica al Orchestrator que reanude un flujo pausado.
     *
     * @return {@code false} cuando no existe estado previo y aplica un reinicio.
     */
    public boolean resumeFlow(UUID executionId, String userInput) {
        log.info("[RESUME] Notifying orchestrator to resume execution: executionId={}", executionId);
        boolean resumed = orchestratorClient.resumeFlow(executionId.toString(), userInput);
        if (resumed) {
            log.info("[RESUME] Orchestrator resumed execution: executionId={}", executionId);
        } else {
            log.warn("[RESUME] No paused state found, fallback restart required: executionId={}", executionId);
        }
        return resumed;
    }

    /**
     * Reinicia el flujo desde el principio cuando el Orchestrator perdió el estado.
     */
    public void restartFlow(UUID executionId) {
        log.info("[RESTART] Starting fallback restart for execution: executionId={}", executionId);
        ProcessExecutionRequest request = processService.getExecutionRequest(executionId);
        processService.updateExecutionStatus(executionId,
                ProcessExecutionStatusRequest.of(ProcessExecutionStatus.IN_PROGRESS));
        startFlow(executionId, resolveTemplateName(request.processTemplateId()), request);
    }

    /**
     * Notifica al Orchestrator que reintente un paso específico.
     */
    public void retryStep(UUID executionId, int stepOrder) {
        log.info("[RETRY-STEP] Notifying orchestrator to retry step: executionId={}, stepOrder={}",
                executionId, stepOrder);
        orchestratorClient.retryStep(executionId.toString(), stepOrder);
        log.info("[RETRY-STEP] Orchestrator acknowledged step retry: executionId={}, stepOrder={}",
                executionId, stepOrder);
    }

    private void startFlow(UUID executionId, String flowName, ProcessExecutionRequest request) {
        FlowStartCommand command = new FlowStartCommand(
                executionId.toString(),
                flowName,
                request.customerId(),
                request.vehicleId(),
                request.salesAgentId());

        log.info("[FLOW] Invoking orchestrator: executionId={}, flowId={}, customerId={}",
                executionId, flowName, request.customerId());
        orchestratorClient.startFlow(command).subscribe(
                ignored -> log.info("[FLOW] Orchestrator finished execution: executionId={}", executionId),
                error -> handleStartFailure(executionId, error));
    }

    private String resolveTemplateName(String processTemplateId) {
        try {
            return processTemplateRepository.findById(UUID.fromString(processTemplateId))
                    .map(ProcessTemplateEntity::getName)
                    .orElse(DEFAULT_FLOW_NAME);
        } catch (IllegalArgumentException ex) {
            log.warn("[FLOW] Invalid processTemplateId, using default flow: {}", processTemplateId);
            return DEFAULT_FLOW_NAME;
        }
    }

    private void handleStartFailure(UUID executionId, Throwable error) {
        log.error("[FLOW] Orchestrator failed to start execution: executionId={}", executionId, error);
        ProcessExecutionStatusRequest request = new ProcessExecutionStatusRequest(
                ProcessExecutionStatus.FAILED,
                null,
                "Orchestrator invocation failed: " + error.getMessage(),
                "No pudimos iniciar tu proceso",
                "Ocurrió un problema al comunicarnos con el servicio de orquestación. Vuelve a intentarlo en unos segundos.",
                "Reintentar inicio",
                "error");
        try {
            processService.updateExecutionStatus(executionId, request);
        } catch (Exception ex) {
            log.error("[FLOW] Could not persist start failure: executionId={}", executionId, ex);
        }
    }
}
