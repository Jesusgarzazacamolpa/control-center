package com.floworchestrator.controlcenter.client;

import com.floworchestrator.controlcenter.exception.OrchestratorUnavailableException;
import com.floworchestrator.controlcenter.model.FlowExecutionEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.util.UriBuilder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Adaptador HTTP del puerto {@link OrchestratorClient}. Construye un único
 * {@link WebClient} a partir del builder autoconfigurado por Spring Boot, de
 * modo que las llamadas propaguen el traceId/spanId por los headers.
 */
@Component
@Slf4j
public class WebClientOrchestratorClient implements OrchestratorClient {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    private final WebClient webClient;

    public WebClientOrchestratorClient(WebClient.Builder webClientBuilder,
                                       @Value("${services.orchestrator.base-url}") String baseUrl) {
        this.webClient = webClientBuilder.baseUrl(baseUrl).build();
    }

    @Override
    public Mono<Void> startFlow(FlowStartCommand command) {
        Map<String, Object> body = new HashMap<>();
        body.put("executionId", command.executionId());
        body.put("flowId", command.flowId());
        body.put("customerId", command.customerId());
        body.put("vehicleId", command.vehicleId());
        body.put("salesAgentId", command.salesAgentId());

        return webClient.post()
                .uri("/api/flows")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .toBodilessEntity()
                .then();
    }

    @Override
    public Flux<FlowExecutionEvent> streamEvents() {
        return webClient.get()
                .uri("/api/flows/stream")
                .accept(MediaType.TEXT_EVENT_STREAM)
                .retrieve()
                .bodyToFlux(FlowExecutionEvent.class);
    }

    /**
     * Reanuda la ejecución con un timeout de 30 s; un 404 del Orchestrator se
     * traduce a {@code false} para que el llamador decida reiniciar el flujo.
     */
    @Override
    public boolean resumeFlow(String executionId, String userInput) {
        try {
            webClient.post()
                    .uri(uriBuilder -> resumeUri(uriBuilder, executionId, userInput))
                    .contentType(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .toBodilessEntity()
                    .block(REQUEST_TIMEOUT);
            return true;
        } catch (WebClientResponseException.NotFound ex) {
            log.warn("[ORCHESTRATOR] No se encontró estado pausado para la ejecución: executionId={}", executionId);
            return false;
        } catch (WebClientException | IllegalStateException ex) {
            throw new OrchestratorUnavailableException("reanudar el flujo " + executionId, ex);
        }
    }

    /**
     * Marca el paso para reintento; cualquier fallo de comunicación se propaga
     * como {@link OrchestratorUnavailableException}.
     */
    @Override
    public void retryStep(String executionId, int stepOrder) {
        try {
            webClient.post()
                    .uri("/api/flows/{executionId}/steps/{stepOrder}/retry", executionId, stepOrder)
                    .contentType(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .toBodilessEntity()
                    .block(REQUEST_TIMEOUT);
        } catch (WebClientException | IllegalStateException ex) {
            throw new OrchestratorUnavailableException(
                    "reintentar el paso " + stepOrder + " de la ejecución " + executionId, ex);
        }
    }

    /**
     * Cancela la ejecución en el Orchestrator; un 404 se traduce a {@code false}
     * porque no hay estado que descartar.
     */
    @Override
    public boolean cancelFlow(String executionId) {
        try {
            webClient.post()
                    .uri("/api/flows/{executionId}/cancel", executionId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .toBodilessEntity()
                    .block(REQUEST_TIMEOUT);
            return true;
        } catch (WebClientResponseException.NotFound ex) {
            log.warn("[ORCHESTRATOR] No se encontró estado cancelable para la ejecución: executionId={}", executionId);
            return false;
        } catch (WebClientException | IllegalStateException ex) {
            throw new OrchestratorUnavailableException("cancelar el flujo " + executionId, ex);
        }
    }

    private URI resumeUri(UriBuilder uriBuilder, String executionId, String userInput) {
        UriBuilder builder = uriBuilder.path("/api/flows/{executionId}/resume");
        if (userInput != null && !userInput.isBlank()) {
            builder.queryParam("userInput", userInput);
        }
        return builder.build(executionId);
    }
}
