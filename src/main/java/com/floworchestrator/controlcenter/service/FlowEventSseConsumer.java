package com.floworchestrator.controlcenter.service;

import com.floworchestrator.controlcenter.client.OrchestratorClient;
import com.floworchestrator.controlcenter.enums.ProcessExecutionStatus;
import com.floworchestrator.controlcenter.enums.StepExecutionStatus;
import com.floworchestrator.controlcenter.model.CreateInterventionRequest;
import com.floworchestrator.controlcenter.model.FlowExecutionEvent;
import com.floworchestrator.controlcenter.model.ProcessExecutionStatusRequest;
import com.floworchestrator.controlcenter.model.StepExecutionStatusRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Consume el stream SSE del Orchestrator y registra el bitácora de pasos y
 * ejecuciones en la base de datos del Control Center.
 * <p>
 * La conexión se inicia bajo demanda: la primera vez que un cliente se suscribe
 * al endpoint SSE de este servicio.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FlowEventSseConsumer {

    private static final int MAX_PROCESSED_EVENTS = 10_000;

    private final ProcessService processService;
    private final OrchestratorClient orchestratorClient;

    private final Sinks.Many<FlowExecutionEvent> sink = Sinks.many().multicast().onBackpressureBuffer();
    private final AtomicBoolean started = new AtomicBoolean(false);
    private final Set<String> processedEvents = Collections.synchronizedSet(
            Collections.newSetFromMap(new LinkedHashMap<>() {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
                    return size() > MAX_PROCESSED_EVENTS;
                }
            }));
    private final Set<String> activeExecutions = ConcurrentHashMap.newKeySet();

    /**
     * Devuelve el flux de eventos. La primera invocación inicia la conexión
     * al stream SSE del Orchestrator.
     */
    public Flux<FlowExecutionEvent> stream() {
        startIfNotStarted();
        return sink.asFlux();
    }

    /**
     * Asegura que la conexión al stream SSE del Orchestrator esté activa.
     */
    public void ensureStarted() {
        startIfNotStarted();
    }

    /**
     * Registra una ejecución como activa para recibir sus eventos SSE.
     */
    public void trackExecution(String executionId) {
        if (executionId != null && !executionId.isBlank()) {
            activeExecutions.add(executionId);
        }
    }

    public void reRegisterExecution(String executionId) {
        if (executionId != null && !executionId.isBlank()) {
            activeExecutions.add(executionId);
            processedEvents.removeIf(key -> key.startsWith(executionId + "|"));
        }
    }

    private void startIfNotStarted() {
        if (started.compareAndSet(false, true)) {
            log.info("[SSE] Conectando al stream de eventos del orquestador");
            orchestratorClient.streamEvents()
                    .subscribe(this::handleEvent, this::handleError, this::handleComplete);
        }
    }

    private void handleEvent(FlowExecutionEvent event) {
        if (event.executionId() == null || event.executionId().isBlank()) {
            log.warn("[SSE] Evento sin executionId; se omite: type={}", event.eventType());
            return;
        }
        if (!activeExecutions.contains(event.executionId())) {
            log.debug("[SSE] Se ignora el evento de una ejecución no activa: type={}, executionId={}",
                    event.eventType(), event.executionId());
            return;
        }
        String eventKey = event.executionId() + "|" + event.eventType() + "|" + event.stepOrder();
        if (!processedEvents.add(eventKey)) {
            log.debug("[SSE] Se omite el evento duplicado: {}", eventKey);
            sink.tryEmitNext(event);
            return;
        }
        if (event.eventType() == null) {
            log.warn("[SSE] Evento sin tipo; se omite: executionId={}", event.executionId());
            return;
        }
        if (isStepEvent(event.eventType()) && event.stepOrder() == null) {
            log.warn("[SSE] Evento de paso sin stepOrder; se omite: type={}, executionId={}",
                    event.eventType(), event.executionId());
            return;
        }

        String previousTraceId = MDC.get("traceId");
        try {
            if (event.traceId() != null && !event.traceId().isBlank()) {
                MDC.put("traceId", event.traceId());
            }
            log.info("[SSE] Evento recibido: type={}, executionId={}, flowId={}, step={}",
                    event.eventType(), event.executionId(), event.flowId(), event.stepName());
            UUID executionId = UUID.fromString(event.executionId());
            switch (event.eventType()) {
                case "STEP_STARTED" -> handleStepStarted(executionId, event);
                case "STEP_COMPLETED" -> handleStepCompleted(executionId, event);
                case "STEP_FAILED" -> handleStepFailed(executionId, event);
                case "STEP_REQUIRES_INTERVENTION" -> handleStepRequiresIntervention(executionId, event);
                case "FLOW_PAUSED" -> log.info("[SSE] Flujo pausado para intervención humana: executionId={}, step={}",
                        event.executionId(), event.stepName());
                case "FLOW_RESUMED" -> {
                    processService.resumeExecution(executionId);
                    log.info("[SSE] Flujo reanudado: executionId={}", event.executionId());
                }
                case "FLOW_COMPLETED" -> {
                    handleFlowCompleted(executionId);
                    activeExecutions.remove(event.executionId());
                }
                case "FLOW_FAILED" -> {
                    handleFlowFailed(executionId, event);
                    activeExecutions.remove(event.executionId());
                }
                default -> log.debug("[SSE] Tipo de evento no manejado: {}", event.eventType());
            }
        } catch (IllegalArgumentException ex) {
            log.warn("[SSE] executionId inválido en el evento: {}", event.executionId(), ex);
        } catch (Exception ex) {
            log.error("[SSE] Error al procesar el evento: type={}, executionId={}",
                    event.eventType(), event.executionId(), ex);
        } finally {
            if (previousTraceId != null) {
                MDC.put("traceId", previousTraceId);
            } else {
                MDC.remove("traceId");
            }
        }
        sink.tryEmitNext(event);
    }

    private boolean isStepEvent(String eventType) {
        return switch (eventType) {
            case "STEP_STARTED", "STEP_COMPLETED", "STEP_FAILED", "STEP_REQUIRES_INTERVENTION" -> true;
            default -> false;
        };
    }

    private void handleStepStarted(UUID executionId, FlowExecutionEvent event) {
        processService.updateStepExecution(executionId, event.stepOrder(),
                StepExecutionStatusRequest.of(StepExecutionStatus.RUNNING));
    }

    private void handleStepCompleted(UUID executionId, FlowExecutionEvent event) {
        processService.updateStepExecution(executionId, event.stepOrder(),
                StepExecutionStatusRequest.of(StepExecutionStatus.COMPLETED));
    }

    private void handleStepFailed(UUID executionId, FlowExecutionEvent event) {
        StepExecutionStatusRequest request = new StepExecutionStatusRequest(
                StepExecutionStatus.FAILED,
                event.errorMessage(),
                event.userErrorTitle(),
                event.userErrorMessage(),
                event.userErrorActionLabel(),
                event.userErrorStatusType());
        processService.updateStepExecution(executionId, event.stepOrder(), request);
    }

    private void handleStepRequiresIntervention(UUID executionId, FlowExecutionEvent event) {
        log.info("[SSE] El paso requiere intervención humana: executionId={}, step={}, error={}",
                event.executionId(), event.stepName(), event.errorMessage());

        StepExecutionStatusRequest stepRequest = new StepExecutionStatusRequest(
                StepExecutionStatus.REQUIRES_INTERVENTION,
                event.errorMessage(),
                event.userErrorTitle(),
                event.userErrorMessage(),
                event.userErrorActionLabel(),
                event.userErrorStatusType());
        processService.updateStepExecution(executionId, event.stepOrder(), stepRequest);

        CreateInterventionRequest interventionRequest = new CreateInterventionRequest(
                event.stepName(),
                event.stepOrder(),
                event.errorMessage(),
                event.userErrorTitle(),
                event.userErrorMessage(),
                event.userErrorActionLabel(),
                event.userErrorStatusType());
        processService.createIntervention(executionId, interventionRequest);
    }

    private void handleFlowCompleted(UUID executionId) {
        processService.updateExecutionStatus(executionId,
                ProcessExecutionStatusRequest.of(ProcessExecutionStatus.COMPLETED));
    }

    private void handleFlowFailed(UUID executionId, FlowExecutionEvent event) {
        ProcessExecutionStatusRequest request = new ProcessExecutionStatusRequest(
                ProcessExecutionStatus.FAILED,
                event.stepName(),
                event.errorMessage(),
                event.userErrorTitle(),
                event.userErrorMessage(),
                event.userErrorActionLabel(),
                event.userErrorStatusType());
        processService.updateExecutionStatus(executionId, request);
    }

    private void handleError(Throwable throwable) {
        log.error("[SSE] Error en el stream de eventos", throwable);
        started.set(false);
    }

    private void handleComplete() {
        log.info("[SSE] Stream de eventos finalizado");
        started.set(false);
    }
}
