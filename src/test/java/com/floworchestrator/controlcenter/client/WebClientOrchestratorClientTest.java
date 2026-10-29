package com.floworchestrator.controlcenter.client;

import com.floworchestrator.controlcenter.exception.OrchestratorUnavailableException;
import com.floworchestrator.controlcenter.model.FlowExecutionEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.client.reactive.MockClientHttpRequest;
import org.springframework.web.reactive.function.BodyInserter;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.server.HandlerStrategies;
import org.springframework.http.codec.HttpMessageWriter;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebClientOrchestratorClientTest {

    private static final BodyInserter.Context BODY_CONTEXT = new BodyInserter.Context() {
        @Override
        public List<HttpMessageWriter<?>> messageWriters() {
            return HandlerStrategies.withDefaults().messageWriters();
        }

        @Override
        public Optional<ServerHttpRequest> serverRequest() {
            return Optional.empty();
        }

        @Override
        public Map<String, Object> hints() {
            return Map.of();
        }
    };

    private final AtomicReference<ClientRequest> captured = new AtomicReference<>();

    private WebClientOrchestratorClient client;

    @BeforeEach
    void setUp() {
        client = null;
    }

    private void stub(Function<ClientRequest, Mono<ClientResponse>> responder) {
        WebClient.Builder builder = WebClient.builder().exchangeFunction(request -> {
            captured.set(request);
            return responder.apply(request);
        });
        client = new WebClientOrchestratorClient(builder, "http://orchestrator.test");
    }

    private static Mono<ClientResponse> okEmpty() {
        return Mono.just(ClientResponse.create(HttpStatus.OK).build());
    }

    private String capturedBody() {
        ClientRequest request = captured.get();
        assertNotNull(request, "No se capturó la petición al Orchestrator");
        MockClientHttpRequest mockRequest = new MockClientHttpRequest(request.method(), request.url());
        request.body().insert(mockRequest, BODY_CONTEXT).block(Duration.ofSeconds(5));
        return mockRequest.getBodyAsString().block(Duration.ofSeconds(5));
    }

    @Test
    @DisplayName("startFlow should POST /api/flows with the command payload")
    void startFlowPostsCommandPayload() {
        stub(request -> okEmpty());

        client.startFlow(new FlowStartCommand(
                        "exec-1", "vehicle-purchase", "customer-004", "vehicle-9", "agent-7"))
                .block(Duration.ofSeconds(5));

        ClientRequest request = captured.get();
        assertEquals("POST", request.method().name());
        assertEquals("http://orchestrator.test/api/flows", request.url().toString());
        assertEquals(MediaType.APPLICATION_JSON, request.headers().getContentType());

        String body = capturedBody();
        assertTrue(body.contains("\"executionId\":\"exec-1\""), body);
        assertTrue(body.contains("\"flowId\":\"vehicle-purchase\""), body);
        assertTrue(body.contains("\"customerId\":\"customer-004\""), body);
        assertTrue(body.contains("\"vehicleId\":\"vehicle-9\""), body);
        assertTrue(body.contains("\"salesAgentId\":\"agent-7\""), body);
    }

    @Test
    @DisplayName("streamEvents should request the SSE endpoint and deserialize events")
    void streamEventsDeserializesServerSentEvents() {
        FlowExecutionEvent expected = new FlowExecutionEvent("exec-1", "vehicle-purchase", "STEP_STARTED",
                1, "Consultar vehículo", "get_vehicle_information", "mcp-domain", "RUNNING",
                null, null, null, null, null, "trace-1", OffsetDateTime.parse("2026-01-15T10:00:00Z"));
        stub(request -> Mono.just(ClientResponse.create(HttpStatus.OK)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_EVENT_STREAM_VALUE)
                .body("id: 1\ndata: {\"executionId\":\"exec-1\",\"flowId\":\"vehicle-purchase\","
                        + "\"eventType\":\"STEP_STARTED\",\"stepOrder\":1,\"stepName\":\"Consultar vehículo\","
                        + "\"toolName\":\"get_vehicle_information\",\"server\":\"mcp-domain\",\"status\":\"RUNNING\","
                        + "\"traceId\":\"trace-1\",\"timestamp\":\"2026-01-15T10:00:00Z\"}\n\n")
                .build()));

        List<FlowExecutionEvent> events = client.streamEvents()
                .collectList()
                .block(Duration.ofSeconds(5));

        assertNotNull(events);
        assertEquals(List.of(expected), events);

        ClientRequest request = captured.get();
        assertEquals("GET", request.method().name());
        assertEquals("http://orchestrator.test/api/flows/stream", request.url().toString());
        assertTrue(request.headers().getAccept().contains(MediaType.TEXT_EVENT_STREAM));
    }

    @Test
    @DisplayName("resumeFlow should return true and forward the user input")
    void resumeFlowForwardsUserInput() {
        stub(request -> okEmpty());

        assertTrue(client.resumeFlow("exec-1", "continuar"));

        ClientRequest request = captured.get();
        assertEquals("POST", request.method().name());
        assertTrue(request.url().getPath().equals("/api/flows/exec-1/resume"), request.url().toString());
        assertNotNull(request.url().getQuery());
        assertTrue(request.url().getQuery().contains("userInput="), request.url().toString());
    }

    @Test
    @DisplayName("resumeFlow should omit the query parameter when user input is blank")
    void resumeFlowOmitsBlankUserInput() {
        stub(request -> okEmpty());

        assertTrue(client.resumeFlow("exec-1", "   "));

        assertTrue(captured.get().url().getQuery() == null
                || !captured.get().url().getQuery().contains("userInput="));
    }

    @Test
    @DisplayName("resumeFlow should return false when the orchestrator has no paused state")
    void resumeFlowReturnsFalseOnNotFound() {
        stub(request -> Mono.just(ClientResponse.create(HttpStatus.NOT_FOUND).build()));

        assertFalse(client.resumeFlow("exec-1", null));
    }

    @Test
    @DisplayName("resumeFlow should map connectivity errors to OrchestratorUnavailableException")
    void resumeFlowMapsErrors() {
        stub(request -> Mono.error(new IllegalStateException("connection refused")));

        assertThrows(OrchestratorUnavailableException.class,
                () -> client.resumeFlow("exec-1", null));
    }

    @Test
    @DisplayName("cancelFlow should POST the cancel endpoint")
    void cancelFlowPostsCancelEndpoint() {
        stub(request -> okEmpty());

        assertTrue(client.cancelFlow("exec-1"));

        ClientRequest request = captured.get();
        assertEquals("POST", request.method().name());
        assertEquals("http://orchestrator.test/api/flows/exec-1/cancel", request.url().toString());
    }

    @Test
    @DisplayName("cancelFlow should return false when the orchestrator has no stored state")
    void cancelFlowReturnsFalseOnNotFound() {
        stub(request -> Mono.just(ClientResponse.create(HttpStatus.NOT_FOUND).build()));

        assertFalse(client.cancelFlow("exec-1"));
    }

    @Test
    @DisplayName("cancelFlow should map connectivity errors to OrchestratorUnavailableException")
    void cancelFlowMapsErrors() {
        stub(request -> Mono.error(new IllegalStateException("connection refused")));

        assertThrows(OrchestratorUnavailableException.class, () -> client.cancelFlow("exec-1"));
    }

    @Test
    @DisplayName("retryStep should POST the retry endpoint for the given step order")
    void retryStepPostsStepOrder() {
        stub(request -> okEmpty());

        client.retryStep("exec-1", 3);

        ClientRequest request = captured.get();
        assertEquals("POST", request.method().name());
        assertEquals("http://orchestrator.test/api/flows/exec-1/steps/3/retry", request.url().toString());
    }

    @Test
    @DisplayName("retryStep should map HTTP errors to OrchestratorUnavailableException")
    void retryStepMapsHttpErrors() {
        stub(request -> Mono.just(ClientResponse.create(HttpStatus.INTERNAL_SERVER_ERROR).build()));

        assertThrows(OrchestratorUnavailableException.class, () -> client.retryStep("exec-1", 3));
    }
}
