package com.floworchestrator.controlcenter.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.floworchestrator.controlcenter.entity.InterventionEntity;
import com.floworchestrator.controlcenter.entity.ProcessExecutionEntity;
import com.floworchestrator.controlcenter.entity.ProcessTemplateEntity;
import com.floworchestrator.controlcenter.entity.StepExecutionEntity;
import com.floworchestrator.controlcenter.entity.StepTemplateEntity;
import com.floworchestrator.controlcenter.enums.InterventionStatus;
import com.floworchestrator.controlcenter.enums.ProcessExecutionStatus;
import com.floworchestrator.controlcenter.enums.StepExecutionStatus;
import com.floworchestrator.controlcenter.error.FlowCode;
import com.floworchestrator.controlcenter.exception.InterventionNotFoundException;
import com.floworchestrator.controlcenter.exception.InvalidInterventionStatusException;
import com.floworchestrator.controlcenter.exception.ProcessNotFoundException;
import com.floworchestrator.controlcenter.exception.ProcessTemplateNotFoundException;
import com.floworchestrator.controlcenter.exception.StepNotFoundException;
import com.floworchestrator.controlcenter.model.CreateInterventionRequest;
import com.floworchestrator.controlcenter.model.ProcessExecutionRequest;
import com.floworchestrator.controlcenter.model.ProcessExecutionResponse;
import com.floworchestrator.controlcenter.model.ProcessExecutionStatusRequest;
import com.floworchestrator.controlcenter.model.StepExecutionStatusRequest;
import com.floworchestrator.controlcenter.model.UpdateInterventionRequest;
import com.floworchestrator.controlcenter.repository.InterventionRepository;
import com.floworchestrator.controlcenter.repository.ProcessExecutionRepository;
import com.floworchestrator.controlcenter.repository.ProcessTemplateRepository;
import com.floworchestrator.controlcenter.repository.StepExecutionRepository;
import com.floworchestrator.controlcenter.repository.StepTemplateRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProcessServiceTest {

    private static final UUID TEMPLATE_ID = UUID.fromString("558e6497-786f-4e23-8c97-3d681864225b");

    @Mock
    private ProcessExecutionRepository repository;

    @Mock
    private ProcessTemplateRepository processTemplateRepository;

    @Mock
    private StepTemplateRepository stepTemplateRepository;

    @Mock
    private StepExecutionRepository stepExecutionRepository;

    @Mock
    private InterventionRepository interventionRepository;

    private ProcessService service;

    @BeforeEach
    void setUp() {
        service = new ProcessService(repository, processTemplateRepository, stepTemplateRepository,
                stepExecutionRepository, interventionRepository, new ObjectMapper());
    }

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    // ==================== plantillas ====================

    @Test
    @DisplayName("should return default template id when active template exists")
    void shouldReturnDefaultTemplateId() {
        ProcessTemplateEntity template = new ProcessTemplateEntity();
        template.setId(TEMPLATE_ID);
        template.setActive(true);
        when(processTemplateRepository.findFirstByNameAndActiveTrue("Compra de vehiculo"))
                .thenReturn(Optional.of(template));

        assertEquals(TEMPLATE_ID.toString(), service.getDefaultProcessTemplateId());
    }

    @Test
    @DisplayName("should throw domain exception when template does not exist")
    void shouldThrowWhenTemplateDoesNotExist() {
        when(processTemplateRepository.findFirstByNameAndActiveTrue("Compra de vehiculo"))
                .thenReturn(Optional.empty());

        ProcessTemplateNotFoundException ex = assertThrows(ProcessTemplateNotFoundException.class,
                () -> service.getDefaultProcessTemplateId());

        assertEquals(FlowCode.TEMPLATE_NOT_FOUND, ex.getFlowCode());
        assertTrue(ex.getUserMessage().contains("plantilla"));
    }

    // ==================== createExecution ====================

    @Test
    @DisplayName("should create execution with persisted state and configured steps")
    void shouldCreateExecutionWithPersistedStateAndSteps() {
        UUID executionId = UUID.randomUUID();
        StepTemplateEntity step = new StepTemplateEntity();
        step.setName("Consultar vehículo");

        when(repository.save(any(ProcessExecutionEntity.class))).thenAnswer(invocation -> {
            ProcessExecutionEntity entity = invocation.getArgument(0);
            entity.setId(executionId);
            return entity;
        });
        when(stepTemplateRepository.findAllByProcessTemplateIdOrderByStepOrderAsc(TEMPLATE_ID))
                .thenReturn(List.of(step));

        ProcessExecutionResponse response = service.createExecution(
                new ProcessExecutionRequest(TEMPLATE_ID.toString(), "customer-004", "vehicle-1", "agent-1"));

        assertEquals(executionId.toString(), response.id());
        assertEquals(TEMPLATE_ID.toString(), response.processTemplateId());
        assertEquals("customer-004", response.customerId());
        assertEquals(ProcessExecutionStatus.PENDING, response.status());
        assertEquals(List.of("Consultar vehículo"), response.steps());
    }

    // ==================== updateExecutionStatus ====================

    @Test
    @DisplayName("should persist failed status, user-facing fields and trace id")
    void shouldPersistFailedStatusAndTraceId() {
        UUID executionId = UUID.randomUUID();
        ProcessExecutionEntity entity = execution(executionId);
        when(repository.findById(executionId)).thenReturn(Optional.of(entity));
        when(repository.save(any(ProcessExecutionEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        MDC.put("traceId", "trace-abc");

        ProcessExecutionStatusRequest request = new ProcessExecutionStatusRequest(
                ProcessExecutionStatus.FAILED,
                "configurar_pago",
                "Payment service unavailable",
                "Error de pago",
                "No se pudo procesar el pago. Verifica los datos e intenta nuevamente.",
                "Reintentar pago",
                "warning");

        ProcessExecutionResponse updated = service.updateExecutionStatus(executionId, request);

        assertEquals(ProcessExecutionStatus.FAILED, updated.status());
        assertEquals("configurar_pago", updated.currentStep());
        assertEquals("Payment service unavailable", updated.errorMessage());
        assertEquals("Error de pago", updated.userErrorTitle());
        assertEquals("Reintentar pago", updated.userErrorActionLabel());
        assertEquals("warning", updated.userErrorStatusType());
        assertEquals("trace-abc", updated.traceId());
        assertNotNull(entity.getCompletedAt());
        assertNotNull(entity.getLastUpdatedAt());
    }

    @Test
    @DisplayName("should clear error fields and set completedAt when execution completes")
    void shouldClearErrorFieldsWhenCompleted() {
        UUID executionId = UUID.randomUUID();
        ProcessExecutionEntity entity = execution(executionId);
        entity.setErrorMessage("some error");
        entity.setUserErrorTitle("some title");
        when(repository.findById(executionId)).thenReturn(Optional.of(entity));
        when(repository.save(any(ProcessExecutionEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        ProcessExecutionResponse updated = service.updateExecutionStatus(executionId,
                ProcessExecutionStatusRequest.of(ProcessExecutionStatus.COMPLETED));

        assertEquals(ProcessExecutionStatus.COMPLETED, updated.status());
        assertNull(updated.errorMessage());
        assertNull(updated.userErrorTitle());
        assertNotNull(entity.getCompletedAt());
    }

    @Test
    @DisplayName("should clear completedAt when execution returns to a non terminal status")
    void shouldClearCompletedAtForNonTerminalStatus() {
        UUID executionId = UUID.randomUUID();
        ProcessExecutionEntity entity = execution(executionId);
        entity.setCompletedAt(OffsetDateTime.now(ZoneOffset.UTC));
        when(repository.findById(executionId)).thenReturn(Optional.of(entity));
        when(repository.save(any(ProcessExecutionEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        service.updateExecutionStatus(executionId, ProcessExecutionStatusRequest.of(ProcessExecutionStatus.IN_PROGRESS));

        assertNull(entity.getCompletedAt());
    }

    @Test
    @DisplayName("should throw not found when updating nonexistent execution")
    void shouldThrowNotFoundWhenUpdatingNonexistentExecution() {
        UUID executionId = UUID.randomUUID();
        when(repository.findById(executionId)).thenReturn(Optional.empty());

        ProcessNotFoundException ex = assertThrows(ProcessNotFoundException.class,
                () -> service.updateExecutionStatus(executionId,
                        ProcessExecutionStatusRequest.of(ProcessExecutionStatus.FAILED)));

        assertEquals(FlowCode.PROCESS_NOT_FOUND, ex.getFlowCode());
    }

    // ==================== updateStepExecution ====================

    @Test
    @DisplayName("should create step execution and update process current step")
    void shouldCreateStepExecutionAndUpdateProcessCurrentStep() {
        UUID executionId = UUID.randomUUID();
        ProcessExecutionEntity process = execution(executionId);
        StepTemplateEntity template = stepTemplate(1, "Consultar vehículo");
        stubStepTransition(process, template, executionId, Optional.empty());

        var step = service.updateStepExecution(executionId, 1,
                StepExecutionStatusRequest.of(StepExecutionStatus.RUNNING));

        assertEquals("Consultar vehículo", step.name());
        assertEquals(StepExecutionStatus.RUNNING, step.status());
        assertEquals(ProcessExecutionStatus.IN_PROGRESS, process.getStatus());
        assertEquals("Consultar vehículo", process.getCurrentStep());
        assertNotNull(step.startedAt());
    }

    @Test
    @DisplayName("should increment retry count when a failed step restarts")
    void shouldIncrementRetryCountWhenFailedStepRestarts() {
        UUID executionId = UUID.randomUUID();
        ProcessExecutionEntity process = execution(executionId);
        StepTemplateEntity template = stepTemplate(1, "Registrar compra");
        StepExecutionEntity existing = new StepExecutionEntity();
        existing.setId(UUID.randomUUID());
        existing.setProcessExecutionId(executionId);
        existing.setStepTemplateId(template.getId());
        existing.setStatus(StepExecutionStatus.FAILED);
        existing.setRetryCount(1);
        stubStepTransition(process, template, executionId, Optional.of(existing));

        var step = service.updateStepExecution(executionId, 1,
                StepExecutionStatusRequest.of(StepExecutionStatus.RUNNING));

        assertEquals(2, step.retryCount());
        assertNull(step.errorMessage());
        assertNotNull(step.startedAt());
    }

    @Test
    @DisplayName("should mark process as failed and copy user-facing fields")
    void shouldMarkProcessAsFailed() {
        UUID executionId = UUID.randomUUID();
        ProcessExecutionEntity process = execution(executionId);
        StepTemplateEntity template = stepTemplate(1, "Registrar compra");
        stubStepTransition(process, template, executionId, Optional.empty());

        StepExecutionStatusRequest request = new StepExecutionStatusRequest(
                StepExecutionStatus.FAILED,
                "Payment failed",
                "Error de pago",
                "No se pudo pagar",
                "Reintentar",
                "error");

        var step = service.updateStepExecution(executionId, 1, request);

        assertEquals(StepExecutionStatus.FAILED, step.status());
        assertEquals("Payment failed", step.errorMessage());
        assertEquals(ProcessExecutionStatus.FAILED, process.getStatus());
        assertEquals("Error de pago", process.getUserErrorTitle());
        assertEquals("Reintentar", process.getUserErrorActionLabel());
        assertNotNull(process.getCompletedAt());
    }

    @Test
    @DisplayName("should pause process when step requires human intervention")
    void shouldPauseProcessWhenStepRequiresIntervention() {
        UUID executionId = UUID.randomUUID();
        ProcessExecutionEntity process = execution(executionId);
        StepTemplateEntity template = stepTemplate(2, "Validar RFC");
        stubStepTransition(process, template, executionId, Optional.empty());

        var step = service.updateStepExecution(executionId, 2, new StepExecutionStatusRequest(
                StepExecutionStatus.REQUIRES_INTERVENTION, "SAT timeout", null, null, null, null));

        assertEquals(ProcessExecutionStatus.PAUSED_FOR_HUMAN, process.getStatus());
        assertNull(step.completedAt());
        assertNull(process.getCompletedAt());
    }

    @Test
    @DisplayName("should clear current step when a step completes")
    void shouldClearCurrentStepWhenStepCompletes() {
        UUID executionId = UUID.randomUUID();
        ProcessExecutionEntity process = execution(executionId);
        process.setCurrentStep("Paso 1");
        StepTemplateEntity template = stepTemplate(1, "Paso 1");
        stubStepTransition(process, template, executionId, Optional.empty());

        var step = service.updateStepExecution(executionId, 1,
                StepExecutionStatusRequest.of(StepExecutionStatus.COMPLETED));

        assertNull(process.getCurrentStep());
        assertNotNull(step.completedAt());
    }

    @Test
    @DisplayName("should throw not found when step template does not exist")
    void shouldThrowNotFoundWhenStepTemplateDoesNotExist() {
        UUID executionId = UUID.randomUUID();
        ProcessExecutionEntity process = execution(executionId);
        when(repository.findById(executionId)).thenReturn(Optional.of(process));
        when(stepTemplateRepository.findByProcessTemplateIdAndStepOrder(TEMPLATE_ID, 99))
                .thenReturn(Optional.empty());

        assertThrows(StepNotFoundException.class, () -> service.updateStepExecution(executionId, 99,
                StepExecutionStatusRequest.of(StepExecutionStatus.RUNNING)));
    }

    @Test
    @DisplayName("should throw not found when updating step of nonexistent execution")
    void shouldThrowNotFoundWhenUpdatingStepOfNonexistentExecution() {
        UUID executionId = UUID.randomUUID();
        when(repository.findById(executionId)).thenReturn(Optional.empty());

        assertThrows(ProcessNotFoundException.class, () -> service.updateStepExecution(executionId, 1,
                StepExecutionStatusRequest.of(StepExecutionStatus.RUNNING)));
    }

    // ==================== createIntervention ====================

    @Test
    @DisplayName("should create pending intervention with user-facing fields")
    void shouldCreatePendingIntervention() {
        UUID executionId = UUID.randomUUID();
        ProcessExecutionEntity process = execution(executionId);
        when(repository.findById(executionId)).thenReturn(Optional.of(process));
        when(interventionRepository.save(any(InterventionEntity.class))).thenAnswer(invocation -> {
            InterventionEntity entity = invocation.getArgument(0);
            entity.setId(UUID.randomUUID());
            return entity;
        });
        when(repository.save(any(ProcessExecutionEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        var intervention = service.createIntervention(executionId, new CreateInterventionRequest(
                "validar_rfc", 3, "SAT timeout",
                "No pudimos validar tu RFC",
                "Hubo un inconveniente al validar tus datos fiscales. Verifica que sean correctos.",
                "Reintentar validación", "warning"));

        assertNotNull(intervention.id());
        assertEquals(executionId.toString(), intervention.processId());
        assertEquals("validar_rfc", intervention.stepName());
        assertEquals(3, intervention.stepOrder());
        assertEquals(InterventionStatus.PENDING_REVIEW, intervention.status());
        assertEquals("No pudimos validar tu RFC", intervention.userErrorTitle());
        assertEquals("Reintentar validación", intervention.userErrorActionLabel());
        assertEquals("warning", intervention.userErrorStatusType());
        assertNotNull(intervention.createdAt());
        assertNotNull(intervention.updatedAt());
        assertEquals("Revisar manualmente y continuar el proceso", intervention.suggestedAction());
    }

    @Test
    @DisplayName("should default step order to zero when it is missing")
    void shouldDefaultStepOrderToZero() {
        UUID executionId = UUID.randomUUID();
        ProcessExecutionEntity process = execution(executionId);
        when(repository.findById(executionId)).thenReturn(Optional.of(process));
        when(interventionRepository.save(any(InterventionEntity.class))).thenAnswer(invocation -> {
            InterventionEntity entity = invocation.getArgument(0);
            entity.setId(UUID.randomUUID());
            return entity;
        });
        when(repository.save(any(ProcessExecutionEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        var intervention = service.createIntervention(executionId,
                new CreateInterventionRequest("paso", null, null, null, null, null, null));

        assertEquals(0, intervention.stepOrder());
    }

    @Test
    @DisplayName("should throw not found when creating intervention for nonexistent execution")
    void shouldThrowNotFoundWhenCreatingInterventionForNonexistentExecution() {
        UUID executionId = UUID.randomUUID();
        when(repository.findById(executionId)).thenReturn(Optional.empty());

        assertThrows(ProcessNotFoundException.class, () -> service.createIntervention(executionId,
                new CreateInterventionRequest(null, null, null, null, null, null, null)));
    }

    // ==================== list / get interventions ====================

    @Test
    @DisplayName("should list all interventions")
    void shouldListAllInterventions() {
        InterventionEntity entity = intervention(UUID.randomUUID(), InterventionStatus.PENDING_REVIEW);
        when(interventionRepository.findAll()).thenReturn(List.of(entity));

        var interventions = service.listInterventions();

        assertEquals(1, interventions.size());
        assertEquals(entity.getId().toString(), interventions.get(0).id());
        assertEquals(InterventionStatus.PENDING_REVIEW, interventions.get(0).status());
    }

    @Test
    @DisplayName("should list interventions filtered by status")
    void shouldListInterventionsByStatus() {
        InterventionEntity entity = intervention(UUID.randomUUID(), InterventionStatus.PENDING_REVIEW);
        when(interventionRepository.findByStatusOrderByCreatedAtDesc(InterventionStatus.PENDING_REVIEW))
                .thenReturn(List.of(entity));

        var interventions = service.listInterventionsByStatus("pending_review");

        assertEquals(1, interventions.size());
        assertEquals(InterventionStatus.PENDING_REVIEW, interventions.get(0).status());
    }

    @Test
    @DisplayName("should reject an unknown intervention status filter")
    void shouldRejectUnknownInterventionStatusFilter() {
        assertThrows(InvalidInterventionStatusException.class,
                () -> service.listInterventionsByStatus("PENDING"));
        verify(interventionRepository, never()).findByStatusOrderByCreatedAtDesc(any());
    }

    @Test
    @DisplayName("should get intervention by id")
    void shouldGetInterventionById() {
        UUID interventionId = UUID.randomUUID();
        when(interventionRepository.findById(interventionId))
                .thenReturn(Optional.of(intervention(interventionId, InterventionStatus.PENDING_REVIEW)));

        var intervention = service.getIntervention(interventionId);

        assertEquals(interventionId.toString(), intervention.id());
        assertEquals("step1", intervention.stepName());
    }

    @Test
    @DisplayName("should throw not found when getting nonexistent intervention")
    void shouldThrowNotFoundWhenGettingNonexistentIntervention() {
        UUID interventionId = UUID.randomUUID();
        when(interventionRepository.findById(interventionId)).thenReturn(Optional.empty());

        assertThrows(InterventionNotFoundException.class, () -> service.getIntervention(interventionId));
    }

    // ==================== updateIntervention ====================

    @Test
    @DisplayName("should approve intervention and resume process")
    void shouldApproveInterventionAndResumeProcess() {
        UUID interventionId = UUID.randomUUID();
        UUID executionId = UUID.randomUUID();
        InterventionEntity entity = intervention(interventionId, InterventionStatus.PENDING_REVIEW);
        entity.setProcessExecutionId(executionId);
        ProcessExecutionEntity process = execution(executionId);
        process.setStatus(ProcessExecutionStatus.PAUSED_FOR_HUMAN);
        process.setErrorMessage("paused");
        when(interventionRepository.findById(interventionId)).thenReturn(Optional.of(entity));
        when(repository.findById(executionId)).thenReturn(Optional.of(process));
        when(repository.save(any(ProcessExecutionEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(interventionRepository.save(any(InterventionEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        var updated = service.updateIntervention(interventionId,
                new UpdateInterventionRequest("APPROVED", "Cliente proporcionó el RFC"));

        assertEquals(InterventionStatus.APPROVED, updated.status());
        assertEquals("Cliente proporcionó el RFC", updated.resolutionNotes());
        assertEquals(ProcessExecutionStatus.IN_PROGRESS, process.getStatus());
        assertNull(process.getErrorMessage());
    }

    @Test
    @DisplayName("should reject intervention without resuming the process")
    void shouldRejectIntervention() {
        UUID interventionId = UUID.randomUUID();
        InterventionEntity entity = intervention(interventionId, InterventionStatus.PENDING_REVIEW);
        when(interventionRepository.findById(interventionId)).thenReturn(Optional.of(entity));
        when(interventionRepository.save(any(InterventionEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        var updated = service.updateIntervention(interventionId,
                new UpdateInterventionRequest("REJECTED", "Cliente canceló"));

        assertEquals(InterventionStatus.REJECTED, updated.status());
        assertEquals("Cliente canceló", updated.resolutionNotes());
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("should accept the approval status case-insensitively")
    void shouldAcceptApprovalCaseInsensitively() {
        UUID interventionId = UUID.randomUUID();
        UUID executionId = UUID.randomUUID();
        InterventionEntity entity = intervention(interventionId, InterventionStatus.PENDING_REVIEW);
        entity.setProcessExecutionId(executionId);
        ProcessExecutionEntity process = execution(executionId);
        when(interventionRepository.findById(interventionId)).thenReturn(Optional.of(entity));
        when(repository.findById(executionId)).thenReturn(Optional.of(process));
        when(repository.save(any(ProcessExecutionEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(interventionRepository.save(any(InterventionEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        var updated = service.updateIntervention(interventionId,
                new UpdateInterventionRequest("approved", null));

        assertEquals(InterventionStatus.APPROVED, updated.status());
        assertEquals(ProcessExecutionStatus.IN_PROGRESS, process.getStatus());
    }

    @Test
    @DisplayName("should update only notes when status is absent")
    void shouldUpdateOnlyNotes() {
        UUID interventionId = UUID.randomUUID();
        InterventionEntity entity = intervention(interventionId, InterventionStatus.PENDING_REVIEW);
        when(interventionRepository.findById(interventionId)).thenReturn(Optional.of(entity));
        when(interventionRepository.save(any(InterventionEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        var updated = service.updateIntervention(interventionId,
                new UpdateInterventionRequest(null, "Notas nuevas"));

        assertEquals("Notas nuevas", updated.resolutionNotes());
        assertEquals(InterventionStatus.PENDING_REVIEW, updated.status());
    }

    @Test
    @DisplayName("should reject unknown intervention status")
    void shouldRejectUnknownInterventionStatus() {
        UUID interventionId = UUID.randomUUID();
        when(interventionRepository.findById(interventionId))
                .thenReturn(Optional.of(intervention(interventionId, InterventionStatus.PENDING_REVIEW)));

        InvalidInterventionStatusException ex = assertThrows(InvalidInterventionStatusException.class,
                () -> service.updateIntervention(interventionId,
                        new UpdateInterventionRequest("UNKNOWN", null)));

        assertEquals(FlowCode.INVALID_INTERVENTION_STATUS, ex.getFlowCode());
        verify(interventionRepository, never()).save(any());
    }

    @Test
    @DisplayName("should throw not found when updating nonexistent intervention")
    void shouldThrowNotFoundWhenUpdatingNonexistentIntervention() {
        UUID interventionId = UUID.randomUUID();
        when(interventionRepository.findById(interventionId)).thenReturn(Optional.empty());

        assertThrows(InterventionNotFoundException.class, () -> service.updateIntervention(interventionId,
                new UpdateInterventionRequest(null, null)));
    }

    // ==================== assignIntervention ====================

    @Test
    @DisplayName("should assign intervention to user")
    void shouldAssignInterventionToUser() {
        UUID interventionId = UUID.randomUUID();
        when(interventionRepository.findById(interventionId))
                .thenReturn(Optional.of(intervention(interventionId, InterventionStatus.PENDING_REVIEW)));
        when(interventionRepository.save(any(InterventionEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        var updated = service.assignIntervention(interventionId, "agent-001");

        assertEquals("agent-001", updated.assignedTo());
        assertEquals(InterventionStatus.PENDING_REVIEW, updated.status());
    }

    @Test
    @DisplayName("should throw not found when assigning nonexistent intervention")
    void shouldThrowNotFoundWhenAssigningNonexistentIntervention() {
        UUID interventionId = UUID.randomUUID();
        when(interventionRepository.findById(interventionId)).thenReturn(Optional.empty());

        assertThrows(InterventionNotFoundException.class,
                () -> service.assignIntervention(interventionId, "agent-001"));
    }

    // ==================== resumeExecution ====================

    @Test
    @DisplayName("should resume execution from PAUSED_FOR_HUMAN to IN_PROGRESS")
    void shouldResumeExecutionFromPausedToInProgress() {
        UUID executionId = UUID.randomUUID();
        ProcessExecutionEntity process = execution(executionId);
        process.setStatus(ProcessExecutionStatus.PAUSED_FOR_HUMAN);
        process.setErrorMessage("some error");
        process.setCurrentStep("step1");
        when(repository.findById(executionId)).thenReturn(Optional.of(process));

        service.resumeExecution(executionId);

        assertEquals(ProcessExecutionStatus.IN_PROGRESS, process.getStatus());
        assertNull(process.getErrorMessage());
        assertNull(process.getCompletedAt());
    }

    @Test
    @DisplayName("should resume execution from FAILED to IN_PROGRESS")
    void shouldResumeExecutionFromFailedToInProgress() {
        UUID executionId = UUID.randomUUID();
        ProcessExecutionEntity process = execution(executionId);
        process.setStatus(ProcessExecutionStatus.FAILED);
        process.setErrorMessage("some error");
        when(repository.findById(executionId)).thenReturn(Optional.of(process));

        service.resumeExecution(executionId);

        assertEquals(ProcessExecutionStatus.IN_PROGRESS, process.getStatus());
        assertNull(process.getErrorMessage());
    }

    @Test
    @DisplayName("should not persist execution that is not paused or failed")
    void shouldNotResumeExecutionThatIsNotPausedOrFailed() {
        UUID executionId = UUID.randomUUID();
        ProcessExecutionEntity process = execution(executionId);
        process.setStatus(ProcessExecutionStatus.IN_PROGRESS);
        when(repository.findById(executionId)).thenReturn(Optional.of(process));

        service.resumeExecution(executionId);

        assertEquals(ProcessExecutionStatus.IN_PROGRESS, process.getStatus());
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("should throw not found when resuming nonexistent execution")
    void shouldThrowNotFoundWhenResumingNonexistentExecution() {
        UUID executionId = UUID.randomUUID();
        when(repository.findById(executionId)).thenReturn(Optional.empty());

        assertThrows(ProcessNotFoundException.class, () -> service.resumeExecution(executionId));
    }

    // ==================== queries ====================

    @Test
    @DisplayName("should list executions with customer id extracted from context")
    void shouldListExecutions() {
        ProcessExecutionEntity entity = execution(UUID.randomUUID());
        entity.setGlobalContext("{\"customerId\":\"customer-001\"}");
        when(repository.findAll()).thenReturn(List.of(entity));
        when(stepTemplateRepository.findAllByProcessTemplateIdOrderByStepOrderAsc(TEMPLATE_ID))
                .thenReturn(List.of(stepTemplate(1, "Paso 1")));

        var executions = service.listExecutions();

        assertEquals(1, executions.size());
        assertEquals("customer-001", executions.get(0).customerId());
        assertEquals(List.of("Paso 1"), executions.get(0).steps());
    }

    @Test
    @DisplayName("should get execution by id")
    void shouldGetExecutionById() {
        UUID executionId = UUID.randomUUID();
        ProcessExecutionEntity entity = execution(executionId);
        entity.setGlobalContext("{\"customerId\":\"customer-002\"}");
        when(repository.findById(executionId)).thenReturn(Optional.of(entity));

        var execution = service.getExecution(executionId);

        assertEquals(executionId.toString(), execution.id());
        assertEquals("customer-002", execution.customerId());
    }

    @Test
    @DisplayName("should return null customer id when context is not valid json")
    void shouldReturnNullCustomerIdWhenContextIsInvalid() {
        UUID executionId = UUID.randomUUID();
        ProcessExecutionEntity entity = execution(executionId);
        entity.setGlobalContext("not-json");
        when(repository.findById(executionId)).thenReturn(Optional.of(entity));

        var execution = service.getExecution(executionId);

        assertNull(execution.customerId());
    }

    @Test
    @DisplayName("should throw not found when execution does not exist")
    void shouldThrowNotFoundWhenGettingExecution() {
        UUID executionId = UUID.randomUUID();
        when(repository.findById(executionId)).thenReturn(Optional.empty());

        assertThrows(ProcessNotFoundException.class, () -> service.getExecution(executionId));
    }

    @Test
    @DisplayName("should get execution request for restart")
    void shouldGetExecutionRequestForRestart() {
        UUID executionId = UUID.randomUUID();
        ProcessExecutionEntity entity = execution(executionId);
        entity.setGlobalContext("{\"customerId\":\"customer-001\"}");
        entity.setVehicleId("vehicle-123");
        entity.setSalesAgentId("agent-77");
        when(repository.findById(executionId)).thenReturn(Optional.of(entity));

        var request = service.getExecutionRequest(executionId);

        assertEquals(TEMPLATE_ID.toString(), request.processTemplateId());
        assertEquals("customer-001", request.customerId());
        assertEquals("vehicle-123", request.vehicleId());
        assertEquals("agent-77", request.salesAgentId());
    }

    @Test
    @DisplayName("should get execution steps ordered by started at")
    void shouldGetExecutionSteps() {
        UUID executionId = UUID.randomUUID();
        when(repository.existsById(executionId)).thenReturn(true);

        StepTemplateEntity template1 = stepTemplate(1, "Paso 1");
        StepTemplateEntity template2 = stepTemplate(2, "Paso 2");
        StepExecutionEntity step1 = stepExecution(executionId, template1.getId(), StepExecutionStatus.COMPLETED);
        StepExecutionEntity step2 = stepExecution(executionId, template2.getId(), StepExecutionStatus.PENDING);

        when(stepExecutionRepository.findAllByProcessExecutionIdOrderByStartedAtAsc(executionId))
                .thenReturn(List.of(step1, step2));
        when(stepTemplateRepository.findAllById(any())).thenReturn(List.of(template1, template2));

        var steps = service.getExecutionSteps(executionId);

        assertEquals(2, steps.size());
        assertEquals("Paso 1", steps.get(0).name());
        assertEquals("Paso 2", steps.get(1).name());
    }

    @Test
    @DisplayName("should throw not found when getting steps for nonexistent execution")
    void shouldThrowNotFoundWhenGettingStepsForNonexistentExecution() {
        UUID executionId = UUID.randomUUID();
        when(repository.existsById(executionId)).thenReturn(false);

        assertThrows(ProcessNotFoundException.class, () -> service.getExecutionSteps(executionId));
    }

    // ==================== fixtures ====================

    private ProcessExecutionEntity execution(UUID executionId) {
        ProcessExecutionEntity entity = new ProcessExecutionEntity();
        entity.setId(executionId);
        entity.setProcessTemplateId(TEMPLATE_ID);
        entity.setStatus(ProcessExecutionStatus.IN_PROGRESS);
        return entity;
    }

    private StepTemplateEntity stepTemplate(int order, String name) {
        StepTemplateEntity template = new StepTemplateEntity();
        template.setId(UUID.randomUUID());
        template.setProcessTemplateId(TEMPLATE_ID);
        template.setStepOrder(order);
        template.setName(name);
        return template;
    }

    private StepExecutionEntity stepExecution(UUID executionId, UUID templateId, StepExecutionStatus status) {
        StepExecutionEntity step = new StepExecutionEntity();
        step.setId(UUID.randomUUID());
        step.setProcessExecutionId(executionId);
        step.setStepTemplateId(templateId);
        step.setStatus(status);
        return step;
    }

    private InterventionEntity intervention(UUID interventionId, InterventionStatus status) {
        InterventionEntity entity = new InterventionEntity();
        entity.setId(interventionId);
        entity.setProcessExecutionId(UUID.randomUUID());
        entity.setStepName("step1");
        entity.setStatus(status);
        entity.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        entity.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        return entity;
    }

    private void stubStepTransition(ProcessExecutionEntity process, StepTemplateEntity template,
                                    UUID executionId, Optional<StepExecutionEntity> existingStep) {
        when(repository.findById(executionId)).thenReturn(Optional.of(process));
        when(stepTemplateRepository.findByProcessTemplateIdAndStepOrder(TEMPLATE_ID, template.getStepOrder()))
                .thenReturn(Optional.of(template));
        when(stepExecutionRepository.findByProcessExecutionIdAndStepTemplateId(executionId, template.getId()))
                .thenReturn(existingStep);
        when(repository.save(any(ProcessExecutionEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(stepExecutionRepository.save(any(StepExecutionEntity.class))).thenAnswer(invocation -> {
            StepExecutionEntity saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(UUID.randomUUID());
            }
            return saved;
        });
    }
}
