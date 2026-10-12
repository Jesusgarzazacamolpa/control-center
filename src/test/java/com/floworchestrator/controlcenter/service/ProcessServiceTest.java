package com.floworchestrator.controlcenter.service;

import com.floworchestrator.controlcenter.model.ProcessExecutionRequest;
import com.floworchestrator.controlcenter.entity.ProcessExecutionEntity;
import com.floworchestrator.controlcenter.repository.ProcessExecutionRepository;
import com.floworchestrator.controlcenter.repository.StepExecutionRepository;
import com.floworchestrator.controlcenter.repository.StepTemplateRepository;
import com.floworchestrator.controlcenter.entity.StepExecutionEntity;
import com.floworchestrator.controlcenter.entity.StepTemplateEntity;
import com.floworchestrator.controlcenter.enums.ProcessExecutionStatus;
import com.floworchestrator.controlcenter.enums.StepExecutionStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;
import java.util.Optional;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProcessServiceTest {

    @Mock
    private ProcessExecutionRepository repository;

    @Mock
    private StepTemplateRepository stepTemplateRepository;

    @Mock
    private StepExecutionRepository stepExecutionRepository;

    @InjectMocks
    private ProcessService service;

    @Test
    void shouldCreateExecutionWithPersistedStateAndSteps() {
        ProcessExecutionEntity persisted = new ProcessExecutionEntity();
        UUID templateId = UUID.fromString("558e6497-786f-4e23-8c97-3d681864225b");

        persisted.setId(UUID.randomUUID());
        persisted.setProcessTemplateId(templateId);
        persisted.setGlobalContext("{\"customerId\":\"customer-004\"}");
        persisted.setStatus(ProcessExecutionStatus.PENDING);

        when(repository.save(any(ProcessExecutionEntity.class))).thenReturn(persisted);
        StepTemplateEntity configuredStep = new StepTemplateEntity();
        configuredStep.setName("Consultar vehículo");
        when(stepTemplateRepository.findAllByProcessTemplateIdOrderByStepOrderAsc(templateId))
            .thenReturn(List.of(configuredStep));

        ProcessExecutionRequest request = new ProcessExecutionRequest();
        request.setProcessTemplateId(templateId.toString());
        request.setCustomerId("customer-004");

        var execution = service.createExecution(request);

        assertNotNull(execution.getId());
        assertEquals(templateId.toString(), execution.getProcessTemplateId());
        assertEquals("customer-004", execution.getCustomerId());
        assertEquals("PENDING", execution.getStatus());
        assertEquals(List.of("Consultar vehículo"), execution.getSteps());
    }

    @Test
    void shouldPersistFailedStatusAndCurrentStep() {
        UUID executionId = UUID.randomUUID();
        ProcessExecutionEntity entity = new ProcessExecutionEntity();
        entity.setId(executionId);
        entity.setProcessTemplateId(UUID.randomUUID());
        entity.setStatus(ProcessExecutionStatus.IN_PROGRESS);
        when(repository.findById(executionId)).thenReturn(Optional.of(entity));
        when(repository.save(any(ProcessExecutionEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var updated = service.updateExecutionStatus(
                executionId,
                ProcessExecutionStatus.FAILED,
                "configurar_pago",
                "Payment service unavailable");

        assertEquals("FAILED", updated.getStatus());
        assertEquals("configurar_pago", updated.getCurrentStep());
        assertEquals("Payment service unavailable", updated.getErrorMessage());
        assertNotNull(entity.getCompletedAt());
        assertNotNull(entity.getLastUpdatedAt());
    }

    @Test
    void shouldCreateStepExecutionAndUpdateProcessCurrentStep() {
        UUID executionId = UUID.randomUUID();
        UUID templateId = UUID.randomUUID();
        ProcessExecutionEntity process = new ProcessExecutionEntity();
        process.setId(executionId);
        process.setProcessTemplateId(templateId);
        StepTemplateEntity stepTemplate = new StepTemplateEntity();
        stepTemplate.setId(UUID.randomUUID());
        stepTemplate.setProcessTemplateId(templateId);
        stepTemplate.setStepOrder(1);
        stepTemplate.setName("Consultar vehículo");

        when(repository.findById(executionId)).thenReturn(Optional.of(process));
        when(stepTemplateRepository.findByProcessTemplateIdAndStepOrder(templateId, 1))
                .thenReturn(Optional.of(stepTemplate));
        when(stepExecutionRepository.findByProcessExecutionIdAndStepTemplateId(executionId, stepTemplate.getId()))
                .thenReturn(Optional.empty());
        when(repository.save(any(ProcessExecutionEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(stepExecutionRepository.save(any(StepExecutionEntity.class))).thenAnswer(invocation -> {
            StepExecutionEntity saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });

        var step = service.updateStepExecution(executionId, 1, StepExecutionStatus.RUNNING, null);

        assertEquals("Consultar vehículo", step.getName());
        assertEquals(StepExecutionStatus.RUNNING, step.getStatus());
        assertEquals("IN_PROGRESS", process.getStatus());
        assertEquals("Consultar vehículo", process.getCurrentStep());
        assertNotNull(step.getStartedAt());
    }
}
