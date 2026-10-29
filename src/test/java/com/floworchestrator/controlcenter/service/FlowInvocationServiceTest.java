package com.floworchestrator.controlcenter.service;

import com.floworchestrator.controlcenter.client.FlowStartCommand;
import com.floworchestrator.controlcenter.client.OrchestratorClient;
import com.floworchestrator.controlcenter.entity.ProcessTemplateEntity;
import com.floworchestrator.controlcenter.enums.ProcessExecutionStatus;
import com.floworchestrator.controlcenter.exception.InvalidProcessStateException;
import com.floworchestrator.controlcenter.exception.OrchestratorUnavailableException;
import com.floworchestrator.controlcenter.model.ProcessExecutionRequest;
import com.floworchestrator.controlcenter.model.ProcessExecutionResponse;
import com.floworchestrator.controlcenter.model.ProcessExecutionStatusRequest;
import com.floworchestrator.controlcenter.repository.ProcessTemplateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FlowInvocationServiceTest {

    private static final UUID EXECUTION_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID TEMPLATE_ID = UUID.fromString("558e6497-786f-4e23-8c97-3d681864225b");

    @Mock
    private ProcessService processService;

    @Mock
    private ProcessTemplateRepository processTemplateRepository;

    @Mock
    private OrchestratorClient orchestratorClient;

    @Mock
    private FlowEventSseConsumer flowEventSseConsumer;

    private FlowInvocationService service;

    @BeforeEach
    void setUp() {
        service = new FlowInvocationService(processService, processTemplateRepository,
                orchestratorClient, flowEventSseConsumer);
    }

    @Test
    @DisplayName("startProcess should track the execution and invoke the orchestrator")
    void startProcessTracksAndInvokesOrchestrator() {
        ProcessExecutionRequest request = request(TEMPLATE_ID.toString());
        ProcessExecutionResponse created = response(ProcessExecutionStatus.PENDING);
        when(processService.createExecution(request)).thenReturn(created);
        when(processTemplateRepository.findById(TEMPLATE_ID)).thenReturn(Optional.of(template()));
        when(orchestratorClient.startFlow(any())).thenReturn(Mono.empty());

        ProcessExecutionResponse result = service.startProcess(request);

        assertEquals(created, result);
        verify(flowEventSseConsumer).ensureStarted();
        verify(flowEventSseConsumer).trackExecution(EXECUTION_ID.toString());

        ArgumentCaptor<FlowStartCommand> captor = ArgumentCaptor.forClass(FlowStartCommand.class);
        verify(orchestratorClient).startFlow(captor.capture());
        FlowStartCommand command = captor.getValue();
        assertEquals(EXECUTION_ID.toString(), command.executionId());
        assertEquals("Compra de vehiculo", command.flowId());
        assertEquals("customer-1", command.customerId());
        assertEquals("vehicle-1", command.vehicleId());
        assertEquals("agent-1", command.salesAgentId());
    }

    @Test
    @DisplayName("startProcess should mark the execution as failed when the orchestrator call fails")
    void startProcessMarksExecutionAsFailed() {
        ProcessExecutionRequest request = request(TEMPLATE_ID.toString());
        when(processService.createExecution(any())).thenReturn(response(ProcessExecutionStatus.PENDING));
        when(processTemplateRepository.findById(TEMPLATE_ID)).thenReturn(Optional.of(template()));
        when(orchestratorClient.startFlow(any())).thenReturn(Mono.error(new RuntimeException("boom")));

        service.startProcess(request);

        verify(processService).updateExecutionStatus(eq(EXECUTION_ID), argThat(status ->
                status.status() == ProcessExecutionStatus.FAILED
                        && "No pudimos iniciar tu proceso".equals(status.userErrorTitle())
                        && status.errorMessage().contains("boom")));
    }

    @Test
    @DisplayName("startProcess should fall back to the default flow for a malformed template id")
    void startProcessUsesDefaultFlowForInvalidTemplateId() {
        when(processService.createExecution(any())).thenReturn(response(ProcessExecutionStatus.PENDING));
        when(orchestratorClient.startFlow(any())).thenReturn(Mono.empty());

        service.startProcess(request("not-a-uuid"));

        ArgumentCaptor<FlowStartCommand> captor = ArgumentCaptor.forClass(FlowStartCommand.class);
        verify(orchestratorClient).startFlow(captor.capture());
        assertEquals("Compra de vehiculo", captor.getValue().flowId());
        verify(processTemplateRepository, never()).findById(any());
    }

    @Test
    @DisplayName("retryProcess should reject executions that are not retryable")
    void retryProcessRejectsNonRetryableExecution() {
        when(processService.getExecution(EXECUTION_ID)).thenReturn(response(ProcessExecutionStatus.IN_PROGRESS));

        assertThrows(InvalidProcessStateException.class, () -> service.retryProcess(EXECUTION_ID, null));
        verify(flowEventSseConsumer, never()).reRegisterExecution(any());
    }

    @Test
    @DisplayName("retryProcess should resume the flow when state exists")
    void retryProcessResumesFlow() {
        when(processService.getExecution(EXECUTION_ID))
                .thenReturn(response(ProcessExecutionStatus.PAUSED_FOR_HUMAN),
                        response(ProcessExecutionStatus.IN_PROGRESS));
        when(orchestratorClient.resumeFlow(EXECUTION_ID.toString(), "RFC-123")).thenReturn(true);

        ProcessExecutionResponse result = service.retryProcess(EXECUTION_ID, "RFC-123");

        assertEquals(ProcessExecutionStatus.IN_PROGRESS, result.status());
        verify(flowEventSseConsumer).reRegisterExecution(EXECUTION_ID.toString());
        verify(orchestratorClient).resumeFlow(EXECUTION_ID.toString(), "RFC-123");
        verify(processService, never()).getExecutionRequest(any());
    }

    @Test
    @DisplayName("retryProcess should restart the flow when the orchestrator has no state")
    void retryProcessRestartsFlowWhenNoState() {
        ProcessExecutionRequest request = request(TEMPLATE_ID.toString());
        when(processService.getExecution(EXECUTION_ID))
                .thenReturn(response(ProcessExecutionStatus.FAILED),
                        response(ProcessExecutionStatus.IN_PROGRESS));
        when(orchestratorClient.resumeFlow(EXECUTION_ID.toString(), null)).thenReturn(false);
        when(processService.getExecutionRequest(EXECUTION_ID)).thenReturn(request);
        when(processTemplateRepository.findById(TEMPLATE_ID)).thenReturn(Optional.of(template()));
        when(orchestratorClient.startFlow(any())).thenReturn(Mono.empty());

        ProcessExecutionResponse result = service.retryProcess(EXECUTION_ID, null);

        assertEquals(ProcessExecutionStatus.IN_PROGRESS, result.status());
        verify(processService).updateExecutionStatus(eq(EXECUTION_ID), argThat(status ->
                status.status() == ProcessExecutionStatus.IN_PROGRESS));
        verify(orchestratorClient).startFlow(any());
    }

    @Test
    @DisplayName("resumeFlow should return true when the orchestrator resumes the flow")
    void resumeFlowReturnsTrue() {
        when(orchestratorClient.resumeFlow(EXECUTION_ID.toString(), null)).thenReturn(true);

        assertTrue(service.resumeFlow(EXECUTION_ID, null));
    }

    @Test
    @DisplayName("resumeFlow should return false when there is no state")
    void resumeFlowReturnsFalse() {
        when(orchestratorClient.resumeFlow(EXECUTION_ID.toString(), null)).thenReturn(false);

        assertFalse(service.resumeFlow(EXECUTION_ID, null));
    }

    @Test
    @DisplayName("resumeOrRestartFlow should resume without restarting when the orchestrator keeps the state")
    void resumeOrRestartFlowResumesWithoutRestart() {
        when(orchestratorClient.resumeFlow(EXECUTION_ID.toString(), "APPROVED")).thenReturn(true);

        assertTrue(service.resumeOrRestartFlow(EXECUTION_ID, "APPROVED"));

        verify(flowEventSseConsumer).reRegisterExecution(EXECUTION_ID.toString());
        verify(processService, never()).getExecutionRequest(any());
        verify(orchestratorClient, never()).startFlow(any());
    }

    @Test
    @DisplayName("resumeOrRestartFlow should restart when the orchestrator lost the paused state")
    void resumeOrRestartFlowRestartsWithoutState() {
        ProcessExecutionRequest request = request(TEMPLATE_ID.toString());
        when(orchestratorClient.resumeFlow(EXECUTION_ID.toString(), "APPROVED")).thenReturn(false);
        when(processService.getExecutionRequest(EXECUTION_ID)).thenReturn(request);
        when(processTemplateRepository.findById(TEMPLATE_ID)).thenReturn(Optional.of(template()));
        when(orchestratorClient.startFlow(any())).thenReturn(Mono.empty());

        assertFalse(service.resumeOrRestartFlow(EXECUTION_ID, "APPROVED"));

        verify(flowEventSseConsumer).reRegisterExecution(EXECUTION_ID.toString());
        verify(processService).updateExecutionStatus(eq(EXECUTION_ID), argThat(status ->
                status.status() == ProcessExecutionStatus.IN_PROGRESS));
        verify(orchestratorClient).startFlow(argThat(command ->
                EXECUTION_ID.toString().equals(command.executionId())));
    }

    @Test
    @DisplayName("restartFlow should mark the execution in progress and start it again")
    void restartFlowStartsExecutionAgain() {
        ProcessExecutionRequest request = request(TEMPLATE_ID.toString());
        when(processService.getExecutionRequest(EXECUTION_ID)).thenReturn(request);
        when(processTemplateRepository.findById(TEMPLATE_ID)).thenReturn(Optional.of(template()));
        when(orchestratorClient.startFlow(any())).thenReturn(Mono.empty());

        service.restartFlow(EXECUTION_ID);

        verify(processService).updateExecutionStatus(eq(EXECUTION_ID), argThat(status ->
                status.status() == ProcessExecutionStatus.IN_PROGRESS));
        verify(orchestratorClient).startFlow(argThat(command ->
                EXECUTION_ID.toString().equals(command.executionId())));
    }

    @Test
    @DisplayName("restartFlow should mark the execution as failed when restart fails")
    void restartFlowMarksExecutionAsFailed() {
        ProcessExecutionRequest request = request(TEMPLATE_ID.toString());
        when(processService.getExecutionRequest(EXECUTION_ID)).thenReturn(request);
        when(processTemplateRepository.findById(TEMPLATE_ID)).thenReturn(Optional.of(template()));
        when(orchestratorClient.startFlow(any())).thenReturn(Mono.error(new RuntimeException("down")));

        service.restartFlow(EXECUTION_ID);

        ArgumentCaptor<ProcessExecutionStatusRequest> captor =
                ArgumentCaptor.forClass(ProcessExecutionStatusRequest.class);
        verify(processService, times(2)).updateExecutionStatus(eq(EXECUTION_ID), captor.capture());
        List<ProcessExecutionStatusRequest> requests = captor.getAllValues();
        assertEquals(ProcessExecutionStatus.IN_PROGRESS, requests.get(0).status());
        assertEquals(ProcessExecutionStatus.FAILED, requests.get(1).status());
    }

    @Test
    @DisplayName("cancelFlow should ask the orchestrator to discard the stored state")
    void cancelFlowDelegatesToOrchestrator() {
        when(orchestratorClient.cancelFlow(EXECUTION_ID.toString())).thenReturn(true);

        assertTrue(service.cancelFlow(EXECUTION_ID));
        verify(orchestratorClient).cancelFlow(EXECUTION_ID.toString());
    }

    @Test
    @DisplayName("cancelFlow should return false when there is nothing to cancel")
    void cancelFlowReturnsFalseWithoutState() {
        when(orchestratorClient.cancelFlow(EXECUTION_ID.toString())).thenReturn(false);

        assertFalse(service.cancelFlow(EXECUTION_ID));
    }

    @Test
    @DisplayName("cancelFlow should propagate orchestrator failures")
    void cancelFlowPropagatesFailures() {
        when(orchestratorClient.cancelFlow(EXECUTION_ID.toString()))
                .thenThrow(new OrchestratorUnavailableException("cancel", new RuntimeException("down")));

        assertThrows(OrchestratorUnavailableException.class, () -> service.cancelFlow(EXECUTION_ID));
    }

    @Test
    @DisplayName("retryStep should mark the step and resume the flow")
    void retryStepDelegatesToOrchestrator() {
        when(orchestratorClient.resumeFlow(EXECUTION_ID.toString(), null)).thenReturn(true);

        service.retryStep(EXECUTION_ID, 2);

        verify(orchestratorClient).retryStep(EXECUTION_ID.toString(), 2);
        verify(flowEventSseConsumer).reRegisterExecution(EXECUTION_ID.toString());
        verify(orchestratorClient).resumeFlow(EXECUTION_ID.toString(), null);
    }

    @Test
    @DisplayName("retryStep should not resume when the orchestrator no longer has state")
    void retryStepSkipsResumeWithoutState() {
        when(orchestratorClient.resumeFlow(EXECUTION_ID.toString(), null)).thenReturn(false);

        service.retryStep(EXECUTION_ID, 2);

        verify(orchestratorClient).retryStep(EXECUTION_ID.toString(), 2);
        verify(orchestratorClient).resumeFlow(EXECUTION_ID.toString(), null);
    }

    @Test
    @DisplayName("retryStep should propagate orchestrator failures")
    void retryStepPropagatesFailures() {
        doThrow(new OrchestratorUnavailableException("retry", new RuntimeException("down")))
                .when(orchestratorClient).retryStep(EXECUTION_ID.toString(), 2);

        assertThrows(OrchestratorUnavailableException.class, () -> service.retryStep(EXECUTION_ID, 2));
    }

    private ProcessExecutionRequest request(String templateId) {
        return new ProcessExecutionRequest(templateId, "customer-1", "vehicle-1", "agent-1");
    }

    private ProcessExecutionResponse response(ProcessExecutionStatus status) {
        return new ProcessExecutionResponse(EXECUTION_ID.toString(), TEMPLATE_ID.toString(), "customer-1",
                status, "Paso 1", null, null, null, null, null, "trace-1", List.of("Paso 1"));
    }

    private ProcessTemplateEntity template() {
        ProcessTemplateEntity template = new ProcessTemplateEntity();
        template.setId(TEMPLATE_ID);
        template.setName("Compra de vehiculo");
        template.setActive(true);
        return template;
    }
}
