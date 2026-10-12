package com.floworchestrator.controlcenter.service;

import com.floworchestrator.controlcenter.entity.ProcessTemplateEntity;
import com.floworchestrator.controlcenter.model.ProcessExecutionRequest;
import com.floworchestrator.controlcenter.model.ProcessExecutionResponse;
import com.floworchestrator.controlcenter.repository.ProcessTemplateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;
import java.util.UUID;

/**
 * Crea la ejecución del proceso en base de datos e invoca al
 * Orchestrator para que ejecute los pasos de los MCPs. El
 * Orchestrator reporta el estatus de cada paso mediante eventos SSE.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FlowInvocationService {

    private final ProcessService processService;
    private final ProcessTemplateRepository processTemplateRepository;
    private final WebClient.Builder webClientBuilder;
    private final FlowEventSseConsumer flowEventSseConsumer;

    @Value("${services.orchestrator.base-url}")
    private String orchestratorBaseUrl;

    /**
     * Crea la ejecución en el Control Center y dispara el flujo en el Orchestrator.
     *
     * @param request Datos de la ejecución (plantilla y cliente).
     * @return La ejecución creada con estatus PENDING.
     */
    public ProcessExecutionResponse startProcess(ProcessExecutionRequest request) {
        flowEventSseConsumer.ensureStarted();

        ProcessExecutionResponse execution = processService.createExecution(request);

        String templateName = processTemplateRepository.findById(UUID.fromString(request.getProcessTemplateId()))
                .map(ProcessTemplateEntity::getName)
                .orElse("Compra de vehiculo");

        invokeOrchestrator(execution.getId(), templateName, request);
        return execution;
    }

    private void invokeOrchestrator(String executionId, String flowId, ProcessExecutionRequest request) {
        log.info("[FLOW] Invoking orchestrator: executionId={}, flowId={}, customerId={}",
                executionId, flowId, request.getCustomerId());

        Map<String, Object> body = new java.util.HashMap<>();
        body.put("executionId", executionId);
        body.put("flowId", flowId);
        body.put("customerId", request.getCustomerId());
        body.put("vehicleId", request.getVehicleId());
        body.put("salesAgentId", request.getSalesAgentId());

        webClientBuilder.build()
                .post()
                .uri(orchestratorBaseUrl + "/api/flows")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(Map.class)
                .subscribe(
                        response -> log.info("[FLOW] Orchestrator finished execution: executionId={}", executionId),
                        error -> log.error("[FLOW] Orchestrator failed to start execution: executionId={}",
                                executionId, error)
                );
    }
}
