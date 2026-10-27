package com.floworchestrator.controlcenter.service;

import com.floworchestrator.controlcenter.client.OrchestratorClient;
import com.floworchestrator.controlcenter.enums.ProcessExecutionStatus;
import com.floworchestrator.controlcenter.enums.StepExecutionStatus;
import com.floworchestrator.controlcenter.model.FlowExecutionEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import reactor.core.publisher.Flux;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FlowEventSseConsumerTest {

    private static final UUID EXECUTION_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Mock
    private ProcessService processService;

    @Mock
    private OrchestratorClient orchestratorClient;

    private FlowEventSseConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new FlowEventSseConsumer(processService, orchestratorClient);
    }

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    @DisplayName("should update the step when STEP_STARTED arrives")
    void stepStartedUpdatesStep() {
        consumer.trackExecution(EXECUTION_ID.toString());
        when(orchestratorClient.streamEvents()).thenReturn(Flux.just(event("STEP_STARTED", 1)));

        consumer.ensureStarted();

        verify(processService).updateStepExecution(eq(EXECUTION_ID), eq(1),
                argThat(request -> request.status() == StepExecutionStatus.RUNNING));
        assertNull(MDC.get("traceId"));
    }

    @Test
    @DisplayName("should process a duplicated event only once")
    void duplicateEventIsProcessedOnce() {
        FlowExecutionEvent event = event("STEP_STARTED", 1);
        consumer.trackExecution(EXECUTION_ID.toString());
        when(orchestratorClient.streamEvents()).thenReturn(Flux.just(event, event));

        consumer.ensureStarted();

        verify(processService, times(1)).updateStepExecution(eq(EXECUTION_ID), eq(1), any());
    }

    @Test
    @DisplayName("should ignore events for executions that are not tracked")
    void nonActiveExecutionIsIgnored() {
        when(orchestratorClient.streamEvents()).thenReturn(Flux.just(event("STEP_STARTED", 1)));

        consumer.ensureStarted();

        verifyNoInteractions(processService);
    }

    @Test
    @DisplayName("should ignore events without executionId")
    void eventWithoutExecutionIdIsIgnored() {
        FlowExecutionEvent event = new FlowExecutionEvent(
                null, "flow-1", "STEP_STARTED", 1, "Paso 1", null, null, null,
                null, null, null, null, null, null, OffsetDateTime.now());
        when(orchestratorClient.streamEvents()).thenReturn(Flux.just(event));

        consumer.ensureStarted();

        verifyNoInteractions(processService);
    }

    @Test
    @DisplayName("should ignore step events without stepOrder")
    void stepEventWithoutStepOrderIsIgnored() {
        consumer.trackExecution(EXECUTION_ID.toString());
        when(orchestratorClient.streamEvents()).thenReturn(Flux.just(event("STEP_STARTED", null)));

        consumer.ensureStarted();

        verifyNoInteractions(processService);
    }

    @Test
    @DisplayName("should persist user-facing fields when STEP_FAILED arrives")
    void stepFailedPersistsUserFacingFields() {
        consumer.trackExecution(EXECUTION_ID.toString());
        when(orchestratorClient.streamEvents()).thenReturn(Flux.just(event("STEP_FAILED", 1)));

        consumer.ensureStarted();

        verify(processService).updateStepExecution(eq(EXECUTION_ID), eq(1), argThat(request ->
                request.status() == StepExecutionStatus.FAILED
                        && "SAT timeout".equals(request.errorMessage())
                        && "No pudimos validar tu RFC".equals(request.userErrorTitle())
                        && "Reintentar".equals(request.userErrorActionLabel())));
    }

    @Test
    @DisplayName("should pause the step and open an intervention when required")
    void requiresInterventionCreatesIntervention() {
        consumer.trackExecution(EXECUTION_ID.toString());
        when(orchestratorClient.streamEvents()).thenReturn(Flux.just(event("STEP_REQUIRES_INTERVENTION", 1)));

        consumer.ensureStarted();

        verify(processService).updateStepExecution(eq(EXECUTION_ID), eq(1), argThat(request ->
                request.status() == StepExecutionStatus.REQUIRES_INTERVENTION));
        verify(processService).createIntervention(eq(EXECUTION_ID), argThat(request ->
                "Validar RFC".equals(request.stepName())
                        && Integer.valueOf(1).equals(request.stepOrder())
                        && "SAT timeout".equals(request.errorMessage())));
    }

    @Test
    @DisplayName("should mark the execution as completed on FLOW_COMPLETED")
    void flowCompletedMarksExecutionCompleted() {
        consumer.trackExecution(EXECUTION_ID.toString());
        when(orchestratorClient.streamEvents()).thenReturn(Flux.just(event("FLOW_COMPLETED", null)));

        consumer.ensureStarted();

        verify(processService).updateExecutionStatus(eq(EXECUTION_ID),
                argThat(request -> request.status() == ProcessExecutionStatus.COMPLETED));
    }

    @Test
    @DisplayName("should mark the execution as failed on FLOW_FAILED")
    void flowFailedMarksExecutionFailed() {
        consumer.trackExecution(EXECUTION_ID.toString());
        when(orchestratorClient.streamEvents()).thenReturn(Flux.just(event("FLOW_FAILED", null)));

        consumer.ensureStarted();

        verify(processService).updateExecutionStatus(eq(EXECUTION_ID), argThat(request ->
                request.status() == ProcessExecutionStatus.FAILED
                        && "Validar RFC".equals(request.currentStep())
                        && "Reintentar".equals(request.userErrorActionLabel())));
    }

    @Test
    @DisplayName("should resume the execution on FLOW_RESUMED")
    void flowResumedResumesExecution() {
        consumer.trackExecution(EXECUTION_ID.toString());
        when(orchestratorClient.streamEvents()).thenReturn(Flux.just(event("FLOW_RESUMED", null)));

        consumer.ensureStarted();

        verify(processService).resumeExecution(EXECUTION_ID);
    }

    @Test
    @DisplayName("should not touch the database on FLOW_PAUSED")
    void flowPausedOnlyLogs() {
        consumer.trackExecution(EXECUTION_ID.toString());
        when(orchestratorClient.streamEvents()).thenReturn(Flux.just(event("FLOW_PAUSED", 1)));

        consumer.ensureStarted();

        verifyNoInteractions(processService);
    }

    @Test
    @DisplayName("should ignore an invalid executionId without failing")
    void invalidExecutionIdIsIgnored() {
        String invalidId = "not-a-uuid";
        consumer.trackExecution(invalidId);
        when(orchestratorClient.streamEvents()).thenReturn(Flux.just(new FlowExecutionEvent(
                invalidId, "flow-1", "STEP_STARTED", 1, "Paso 1", null, null, null,
                null, null, null, null, null, "trace", OffsetDateTime.now())));

        consumer.ensureStarted();

        verifyNoInteractions(processService);
    }

    @Test
    @DisplayName("should ignore unhandled event types")
    void unknownEventTypeIsIgnored() {
        consumer.trackExecution(EXECUTION_ID.toString());
        when(orchestratorClient.streamEvents()).thenReturn(Flux.just(event("UNKNOWN_TYPE", 1)));

        consumer.ensureStarted();

        verifyNoInteractions(processService);
    }

    @Test
    @DisplayName("should ignore events without type")
    void eventWithoutTypeIsIgnored() {
        consumer.trackExecution(EXECUTION_ID.toString());
        when(orchestratorClient.streamEvents()).thenReturn(Flux.just(event(null, 1)));

        consumer.ensureStarted();

        verifyNoInteractions(processService);
    }

    @Test
    @DisplayName("should reprocess events after re-registering an execution")
    void reRegisterAllowsReprocessing() {
        FlowExecutionEvent event = event("STEP_STARTED", 1);
        consumer.trackExecution(EXECUTION_ID.toString());
        when(orchestratorClient.streamEvents()).thenReturn(Flux.just(event), Flux.just(event));

        consumer.ensureStarted();
        verify(processService, times(1)).updateStepExecution(eq(EXECUTION_ID), eq(1), any());

        consumer.reRegisterExecution(EXECUTION_ID.toString());
        consumer.ensureStarted();
        verify(processService, times(2)).updateStepExecution(eq(EXECUTION_ID), eq(1), any());
    }

    @Test
    @DisplayName("should not track blank execution ids")
    void blankExecutionIdIsNotTracked() {
        consumer.trackExecution(" ");
        consumer.reRegisterExecution("");
        when(orchestratorClient.streamEvents()).thenReturn(Flux.just(event("STEP_STARTED", 1)));

        consumer.ensureStarted();

        verify(processService, never()).updateStepExecution(any(), anyInt(), any());
    }

    private FlowExecutionEvent event(String type, Integer stepOrder) {
        return new FlowExecutionEvent(
                EXECUTION_ID.toString(),
                "flow-1",
                type,
                stepOrder,
                "Validar RFC",
                "validate_rfc",
                "mcp-domain",
                "FAILED",
                "SAT timeout",
                "No pudimos validar tu RFC",
                "Hubo un inconveniente al validar tus datos fiscales.",
                "Reintentar",
                "warning",
                "orch-trace-1",
                OffsetDateTime.now());
    }
}
