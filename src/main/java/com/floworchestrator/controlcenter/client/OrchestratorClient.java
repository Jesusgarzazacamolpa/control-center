package com.floworchestrator.controlcenter.client;

import com.floworchestrator.controlcenter.model.FlowExecutionEvent;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Puerto de salida hacia el Orchestrator. Aísla a los casos de uso del cliente
 * HTTP concreto y permite sustituirlo en pruebas.
 */
public interface OrchestratorClient {

    /**
     * Dispara la ejecución del flujo. La respuesta del Orchestrator puede tardar
     * todo el flujo, por lo que el resultado se consume de forma asíncrona.
     */
    Mono<Void> startFlow(FlowStartCommand command);

    /**
     * Stream de eventos SSE publicado por el Orchestrator.
     */
    Flux<FlowExecutionEvent> streamEvents();

    /**
     * Solicita reanudar una ejecución pausada.
     *
     * @return {@code false} cuando el Orchestrator no tiene estado previo (404).
     */
    boolean resumeFlow(String executionId, String userInput);

    /**
     * Solicita reintentar un paso específico.
     */
    void retryStep(String executionId, int stepOrder);

    /**
     * Solicita cancelar una ejecución y descartar su estado guardado.
     *
     * @return {@code false} cuando el Orchestrator no tiene estado que cancelar (404).
     */
    boolean cancelFlow(String executionId);
}
