package com.floworchestrator.controlcenter.service;

import com.floworchestrator.controlcenter.enums.ProcessExecutionStatus;
import com.floworchestrator.controlcenter.enums.StepExecutionStatus;
import com.floworchestrator.controlcenter.model.FlowExecutionEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Consume el stream SSE del Orchestrator y realiza el bitácora de pasos
 * y ejecuciones en la base de datos del Control Center.
 * <p>
 * La conexión al Orchestrator se inicia bajo demanda: la primera vez que un
 * cliente se suscribe al endpoint SSE de este servicio.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FlowEventSseConsumer {

    private static final int MAX_PROCESSED_EVENTS = 10_000;

    private final ProcessService processService;
    private final WebClient.Builder webClientBuilder;

    @Value("${services.orchestrator.base-url}")
    private String orchestratorBaseUrl;

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
     *
     * @return Flux de eventos de ejecución.
     */
    public Flux<FlowExecutionEvent> stream() {
        startIfNotStarted();
        return sink.asFlux();
    }

    /**
     * Asegura que la conexión al stream SSE del Orchestrator esté activa.
     * Se invoca al iniciar un proceso para garantizar el bitácora de pasos.
     */
    public void ensureStarted() {
        startIfNotStarted();
    }

    /**
     * Registra una ejecución como activa para recibir sus eventos SSE.
     * Los eventos de ejecuciones no registradas (por ejemplo, ejecuciones
     * anteriores entregadas por el buffer de replay) se ignoran.
     *
     * @param executionId ID de la ejecución a rastrear.
     */
    public void trackExecution(String executionId) {
        if (executionId != null && !executionId.isBlank()) {
            activeExecutions.add(executionId);
        }
    }

    private void startIfNotStarted() {
        if (started.compareAndSet(false, true)) {
            log.info("[SSE] Connecting to orchestrator event stream: {}/api/flows/stream", orchestratorBaseUrl);
            webClientBuilder.build()
                    .get()
                    .uri(orchestratorBaseUrl + "/api/flows/stream")
                    .accept(MediaType.TEXT_EVENT_STREAM)
                    .retrieve()
                    .bodyToFlux(FlowExecutionEvent.class)
                    .subscribe(this::handleEvent, this::handleError, this::handleComplete);
        }
    }

    private void handleEvent(FlowExecutionEvent event) {
        if (event.executionId() == null || event.executionId().isBlank()) {
            log.warn("[SSE] Event without executionId, skipping: {}", event.eventType());
            return;
        }
        if (!activeExecutions.contains(event.executionId())) {
            log.debug("[SSE] Ignoring event for non-active execution: type={}, executionId={}",
                    event.eventType(), event.executionId());
            return;
        }
        String eventKey = event.executionId() + "|" + event.eventType() + "|" + event.stepOrder();
        if (!processedEvents.add(eventKey)) {
            log.debug("[SSE] Skipping duplicate event: {}", eventKey);
            sink.tryEmitNext(event);
            return;
        }
        try {
            log.info("[SSE] Received event: type={}, executionId={}, flowId={}, step={}",
                    event.eventType(), event.executionId(), event.flowId(), event.stepName());
            UUID executionId = UUID.fromString(event.executionId());
            switch (event.eventType()) {
                case "STEP_STARTED" -> handleStepStarted(executionId, event);
                case "STEP_COMPLETED" -> handleStepCompleted(executionId, event);
                case "STEP_FAILED" -> handleStepFailed(executionId, event);
                case "FLOW_COMPLETED" -> {
                    handleFlowCompleted(executionId);
                    activeExecutions.remove(event.executionId());
                }
                case "FLOW_FAILED" -> {
                    handleFlowFailed(executionId, event);
                    activeExecutions.remove(event.executionId());
                }
                default -> log.debug("[SSE] Unhandled event type: {}", event.eventType());
            }
        } catch (IllegalArgumentException ex) {
            log.warn("[SSE] Invalid executionId in event: {}", event.executionId(), ex);
        } catch (Exception ex) {
            log.error("[SSE] Error processing event: type={}, executionId={}",
                    event.eventType(), event.executionId(), ex);
        }
        sink.tryEmitNext(event);
    }

    private void handleStepStarted(UUID executionId, FlowExecutionEvent event) {
        processService.updateStepExecution(
                executionId,
                event.stepOrder(),
                StepExecutionStatus.RUNNING,
                null);
    }

    private void handleStepCompleted(UUID executionId, FlowExecutionEvent event) {
        processService.updateStepExecution(
                executionId,
                event.stepOrder(),
                StepExecutionStatus.COMPLETED,
                null);
    }

    private void handleStepFailed(UUID executionId, FlowExecutionEvent event) {
        processService.updateStepExecution(
                executionId,
                event.stepOrder(),
                StepExecutionStatus.FAILED,
                event.errorMessage());
    }

    private void handleFlowCompleted(UUID executionId) {
        processService.updateExecutionStatus(
                executionId,
                ProcessExecutionStatus.COMPLETED,
                null,
                null);
    }

    private void handleFlowFailed(UUID executionId, FlowExecutionEvent event) {
        processService.updateExecutionStatus(
                executionId,
                ProcessExecutionStatus.FAILED,
                event.stepName(),
                event.errorMessage());
    }

    private void handleError(Throwable throwable) {
        log.error("[SSE] Event stream error", throwable);
        started.set(false);
    }

    private void handleComplete() {
        log.info("[SSE] Event stream completed");
        started.set(false);
    }
}
