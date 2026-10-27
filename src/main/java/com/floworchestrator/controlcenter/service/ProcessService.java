package com.floworchestrator.controlcenter.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.floworchestrator.controlcenter.entity.InterventionEntity;
import com.floworchestrator.controlcenter.entity.ProcessExecutionEntity;
import com.floworchestrator.controlcenter.entity.ProcessTemplateEntity;
import com.floworchestrator.controlcenter.entity.StepExecutionEntity;
import com.floworchestrator.controlcenter.entity.StepTemplateEntity;
import com.floworchestrator.controlcenter.enums.InterventionStatus;
import com.floworchestrator.controlcenter.enums.ProcessExecutionStatus;
import com.floworchestrator.controlcenter.enums.StepExecutionStatus;
import com.floworchestrator.controlcenter.exception.InterventionNotFoundException;
import com.floworchestrator.controlcenter.exception.InvalidInterventionStatusException;
import com.floworchestrator.controlcenter.exception.ProcessNotFoundException;
import com.floworchestrator.controlcenter.exception.ProcessTemplateNotFoundException;
import com.floworchestrator.controlcenter.exception.StepNotFoundException;
import com.floworchestrator.controlcenter.model.CreateInterventionRequest;
import com.floworchestrator.controlcenter.model.InterventionResponse;
import com.floworchestrator.controlcenter.model.ProcessExecutionRequest;
import com.floworchestrator.controlcenter.model.ProcessExecutionResponse;
import com.floworchestrator.controlcenter.model.ProcessExecutionStatusRequest;
import com.floworchestrator.controlcenter.model.ProcessExecutionStepResponse;
import com.floworchestrator.controlcenter.model.StepExecutionStatusRequest;
import com.floworchestrator.controlcenter.model.UpdateInterventionRequest;
import com.floworchestrator.controlcenter.repository.InterventionRepository;
import com.floworchestrator.controlcenter.repository.ProcessExecutionRepository;
import com.floworchestrator.controlcenter.repository.ProcessTemplateRepository;
import com.floworchestrator.controlcenter.repository.StepExecutionRepository;
import com.floworchestrator.controlcenter.repository.StepTemplateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProcessService {

    private static final String DEFAULT_TEMPLATE_NAME = "Compra de vehiculo";
    private static final String DEFAULT_SUGGESTED_ACTION = "Revisar manualmente y continuar el proceso";

    private final ProcessExecutionRepository repository;
    private final ProcessTemplateRepository processTemplateRepository;
    private final StepTemplateRepository stepTemplateRepository;
    private final StepExecutionRepository stepExecutionRepository;
    private final InterventionRepository interventionRepository;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public String getDefaultProcessTemplateId() {
        return getActiveProcessTemplateId(DEFAULT_TEMPLATE_NAME);
    }

    @Transactional(readOnly = true)
    public String getActiveProcessTemplateId(String name) {
        return processTemplateRepository.findFirstByNameAndActiveTrue(name)
                .map(template -> template.getId().toString())
                .orElseThrow(() -> new ProcessTemplateNotFoundException(name));
    }

    /**
     * Crea una nueva ejecución de flujo en estado PENDING con el contexto del cliente.
     */
    @Transactional
    public ProcessExecutionResponse createExecution(ProcessExecutionRequest request) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        ProcessExecutionEntity entity = new ProcessExecutionEntity();
        entity.setProcessTemplateId(UUID.fromString(request.processTemplateId()));
        entity.setStatus(ProcessExecutionStatus.PENDING);
        entity.setGlobalContext(buildGlobalContext(request.customerId()));
        entity.setVehicleId(request.vehicleId());
        entity.setSalesAgentId(request.salesAgentId());
        entity.setStartedAt(now);
        entity.setLastUpdatedAt(now);
        return toResponse(repository.save(entity));
    }

    @Transactional
    public ProcessExecutionResponse updateExecutionStatus(UUID executionId,
                                                          ProcessExecutionStatusRequest request) {
        ProcessExecutionEntity entity = getExecutionEntity(executionId);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        entity.setStatus(request.status());
        entity.setCurrentStep(request.currentStep());
        entity.setErrorMessage(request.errorMessage());
        entity.setUserErrorTitle(request.userErrorTitle());
        entity.setUserErrorMessage(request.userErrorMessage());
        entity.setUserErrorActionLabel(request.userErrorActionLabel());
        entity.setUserErrorStatusType(request.userErrorStatusType());
        applyTraceId(entity);
        entity.setLastUpdatedAt(now);
        entity.setCompletedAt(request.status().isTerminal() ? now : null);
        return toResponse(repository.save(entity));
    }

    @Transactional
    public ProcessExecutionStepResponse updateStepExecution(UUID executionId, int stepOrder,
                                                            StepExecutionStatusRequest request) {
        ProcessExecutionEntity process = getExecutionEntity(executionId);
        StepTemplateEntity template = stepTemplateRepository
                .findByProcessTemplateIdAndStepOrder(process.getProcessTemplateId(), stepOrder)
                .orElseThrow(() -> new StepNotFoundException(process.getProcessTemplateId(), stepOrder));
        StepExecutionEntity step = stepExecutionRepository
                .findByProcessExecutionIdAndStepTemplateId(executionId, template.getId())
                .orElseGet(() -> newStep(executionId, template.getId()));

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        applyStepStatus(process, step, template, request, now);

        step.setStatus(request.status());
        step.setErrorMessage(request.errorMessage());
        process.setUserErrorTitle(request.userErrorTitle());
        process.setUserErrorMessage(request.userErrorMessage());
        process.setUserErrorActionLabel(request.userErrorActionLabel());
        process.setUserErrorStatusType(request.userErrorStatusType());
        applyTraceId(process);
        process.setLastUpdatedAt(now);

        repository.save(process);
        StepExecutionEntity saved = stepExecutionRepository.save(step);
        return toStepResponse(saved, template);
    }

    @Transactional(readOnly = true)
    public List<ProcessExecutionResponse> listExecutions() {
        return repository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ProcessExecutionResponse getExecution(UUID executionId) {
        return toResponse(getExecutionEntity(executionId));
    }

    @Transactional(readOnly = true)
    public ProcessExecutionRequest getExecutionRequest(UUID executionId) {
        ProcessExecutionEntity entity = getExecutionEntity(executionId);
        return new ProcessExecutionRequest(
                entity.getProcessTemplateId().toString(),
                extractCustomerId(entity),
                entity.getVehicleId(),
                entity.getSalesAgentId());
    }

    @Transactional(readOnly = true)
    public List<ProcessExecutionStepResponse> getExecutionSteps(UUID executionId) {
        if (!repository.existsById(executionId)) {
            throw new ProcessNotFoundException(executionId);
        }
        List<StepExecutionEntity> steps = stepExecutionRepository
                .findAllByProcessExecutionIdOrderByStartedAtAsc(executionId);
        Map<UUID, StepTemplateEntity> templates = stepTemplateRepository
                .findAllById(steps.stream().map(StepExecutionEntity::getStepTemplateId).toList())
                .stream()
                .collect(Collectors.toMap(StepTemplateEntity::getId, Function.identity()));
        return steps.stream()
                .map(step -> toStepResponse(step, templates.get(step.getStepTemplateId())))
                .toList();
    }

    @Transactional
    public InterventionResponse createIntervention(UUID executionId, CreateInterventionRequest request) {
        ProcessExecutionEntity process = getExecutionEntity(executionId);

        InterventionEntity entity = new InterventionEntity();
        entity.setProcessExecutionId(executionId);
        entity.setStepName(request.stepName());
        entity.setStepOrder(request.stepOrder() != null ? request.stepOrder() : 0);
        entity.setStatus(InterventionStatus.PENDING_REVIEW);
        entity.setErrorMessage(request.errorMessage());
        entity.setUserErrorTitle(request.userErrorTitle());
        entity.setUserErrorMessage(request.userErrorMessage());
        entity.setUserErrorActionLabel(request.userErrorActionLabel());
        entity.setUserErrorStatusType(request.userErrorStatusType());
        String traceId = MDC.get("traceId");
        if (traceId != null && !traceId.isBlank()) {
            entity.setTraceId(traceId);
        }
        entity.setSuggestedAction(DEFAULT_SUGGESTED_ACTION);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);

        InterventionEntity saved = interventionRepository.save(entity);
        process.setLastUpdatedAt(now);
        repository.save(process);

        log.info("[INTERVENTION] Created intervention: id={}, processId={}, step={}",
                saved.getId(), executionId, request.stepName());
        return toInterventionResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<InterventionResponse> listInterventions() {
        return interventionRepository.findAll().stream()
                .map(this::toInterventionResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<InterventionResponse> listInterventionsByStatus(String rawStatus) {
        InterventionStatus status = parseInterventionStatus(rawStatus.trim().toUpperCase(Locale.ROOT));
        return interventionRepository.findByStatusOrderByCreatedAtDesc(status).stream()
                .map(this::toInterventionResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public InterventionResponse getIntervention(UUID interventionId) {
        return toInterventionResponse(getInterventionEntity(interventionId));
    }

    @Transactional
    public InterventionResponse updateIntervention(UUID interventionId, UpdateInterventionRequest request) {
        InterventionEntity entity = getInterventionEntity(interventionId);

        if (request.status() != null) {
            applyInterventionStatus(entity, request.status());
        }
        if (request.notes() != null) {
            entity.setResolutionNotes(request.notes());
        }
        entity.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));

        InterventionEntity saved = interventionRepository.save(entity);
        log.info("[INTERVENTION] Updated intervention: id={}, status={}", saved.getId(), saved.getStatus());
        return toInterventionResponse(saved);
    }

    @Transactional
    public InterventionResponse assignIntervention(UUID interventionId, String assignee) {
        InterventionEntity entity = getInterventionEntity(interventionId);
        entity.setAssignedTo(assignee);
        entity.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));

        InterventionEntity saved = interventionRepository.save(entity);
        log.info("[INTERVENTION] Assigned intervention: id={}, assignee={}", saved.getId(), assignee);
        return toInterventionResponse(saved);
    }

    @Transactional
    public void resumeExecution(UUID executionId) {
        ProcessExecutionEntity process = getExecutionEntity(executionId);
        if (!process.getStatus().isRetryable()) {
            log.debug("[INTERVENTION] Execution is not paused or failed, skipping resume: id={}, status={}",
                    executionId, process.getStatus());
            return;
        }
        process.setStatus(ProcessExecutionStatus.IN_PROGRESS);
        process.setErrorMessage(null);
        process.setCompletedAt(null);
        process.setLastUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        repository.save(process);
        log.info("[INTERVENTION] Execution resumed via SSE event: id={}", executionId);
    }

    private void applyInterventionStatus(InterventionEntity entity, String rawStatus) {
        InterventionStatus status = parseInterventionStatus(rawStatus.trim().toUpperCase(Locale.ROOT));
        entity.setStatus(status);
        if (status == InterventionStatus.APPROVED) {
            processExecutionAfterResume(entity.getProcessExecutionId(), entity.getStepName());
        }
    }

    private InterventionStatus parseInterventionStatus(String status) {
        try {
            return InterventionStatus.valueOf(status);
        } catch (IllegalArgumentException ex) {
            throw new InvalidInterventionStatusException(status);
        }
    }

    private void processExecutionAfterResume(UUID executionId, String currentStep) {
        ProcessExecutionEntity process = getExecutionEntity(executionId);
        process.setStatus(ProcessExecutionStatus.IN_PROGRESS);
        process.setCurrentStep(currentStep);
        process.setErrorMessage(null);
        process.setLastUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        repository.save(process);
        log.info("[INTERVENTION] Resumed process execution: id={}", executionId);
    }

    private ProcessExecutionEntity getExecutionEntity(UUID executionId) {
        return repository.findById(executionId)
                .orElseThrow(() -> new ProcessNotFoundException(executionId));
    }

    private InterventionEntity getInterventionEntity(UUID interventionId) {
        return interventionRepository.findById(interventionId)
                .orElseThrow(() -> new InterventionNotFoundException(interventionId));
    }

    private String buildGlobalContext(String customerId) {
        try {
            return objectMapper.writeValueAsString(Map.of("customerId", customerId));
        } catch (JsonProcessingException ex) {
            log.warn("[PROCESS] Could not serialize global context for customerId={}", customerId, ex);
            return "{}";
        }
    }

    private void applyStepStatus(ProcessExecutionEntity process, StepExecutionEntity step,
                                 StepTemplateEntity template, StepExecutionStatusRequest request,
                                 OffsetDateTime now) {
        switch (request.status()) {
            case RUNNING -> applyRunningStatus(process, step, template, now);
            case COMPLETED -> applyCompletedStatus(process, step, now);
            case FAILED -> applyFailedStatus(process, step, template, request.errorMessage(), now);
            case REQUIRES_INTERVENTION -> applyInterventionStatus(process, step, template, request.errorMessage());
            case PENDING -> log.debug("[PROCESS] Step marked as PENDING: stepOrder={}", template.getStepOrder());
        }
    }

    private void applyRunningStatus(ProcessExecutionEntity process, StepExecutionEntity step,
                                    StepTemplateEntity template, OffsetDateTime now) {
        if (step.getStatus() == StepExecutionStatus.FAILED) {
            step.setRetryCount(step.getRetryCount() + 1);
        }
        step.setStartedAt(now);
        step.setCompletedAt(null);
        step.setErrorMessage(null);
        process.setStatus(ProcessExecutionStatus.IN_PROGRESS);
        process.setCurrentStep(template.getName());
        process.setErrorMessage(null);
        process.setCompletedAt(null);
    }

    private void applyCompletedStatus(ProcessExecutionEntity process, StepExecutionEntity step,
                                      OffsetDateTime now) {
        step.setCompletedAt(now);
        process.setCurrentStep(null);
        process.setErrorMessage(null);
    }

    private void applyFailedStatus(ProcessExecutionEntity process, StepExecutionEntity step,
                                   StepTemplateEntity template, String errorMessage, OffsetDateTime now) {
        step.setCompletedAt(now);
        process.setStatus(ProcessExecutionStatus.FAILED);
        process.setCurrentStep(template.getName());
        process.setErrorMessage(errorMessage);
        process.setCompletedAt(now);
    }

    private void applyInterventionStatus(ProcessExecutionEntity process, StepExecutionEntity step,
                                         StepTemplateEntity template, String errorMessage) {
        step.setCompletedAt(null);
        process.setStatus(ProcessExecutionStatus.PAUSED_FOR_HUMAN);
        process.setCurrentStep(template.getName());
        process.setErrorMessage(errorMessage);
        process.setCompletedAt(null);
    }

    private StepExecutionEntity newStep(UUID executionId, UUID stepTemplateId) {
        StepExecutionEntity step = new StepExecutionEntity();
        step.setProcessExecutionId(executionId);
        step.setStepTemplateId(stepTemplateId);
        step.setRetryCount(0);
        return step;
    }

    private void applyTraceId(ProcessExecutionEntity entity) {
        String traceId = MDC.get("traceId");
        if (traceId != null && !traceId.isBlank()) {
            entity.setTraceId(traceId);
        }
    }

    private InterventionResponse toInterventionResponse(InterventionEntity entity) {
        return new InterventionResponse(
                entity.getId().toString(),
                entity.getProcessExecutionId().toString(),
                entity.getStepName(),
                entity.getStepOrder(),
                entity.getStatus(),
                entity.getErrorMessage(),
                entity.getUserErrorTitle(),
                entity.getUserErrorMessage(),
                entity.getUserErrorActionLabel(),
                entity.getUserErrorStatusType(),
                entity.getTraceId(),
                entity.getAssignedTo(),
                entity.getResolutionNotes(),
                entity.getSuggestedAction(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }

    private ProcessExecutionStepResponse toStepResponse(StepExecutionEntity step, StepTemplateEntity template) {
        return new ProcessExecutionStepResponse(
                step.getId().toString(),
                template.getStepOrder(),
                template.getName(),
                step.getStatus(),
                step.getRetryCount(),
                step.getErrorMessage(),
                step.getStartedAt(),
                step.getCompletedAt());
    }

    private ProcessExecutionResponse toResponse(ProcessExecutionEntity entity) {
        return new ProcessExecutionResponse(
                entity.getId().toString(),
                entity.getProcessTemplateId().toString(),
                extractCustomerId(entity),
                entity.getStatus(),
                entity.getCurrentStep(),
                entity.getErrorMessage(),
                entity.getUserErrorTitle(),
                entity.getUserErrorMessage(),
                entity.getUserErrorActionLabel(),
                entity.getUserErrorStatusType(),
                entity.getTraceId(),
                stepNames(entity.getProcessTemplateId()));
    }

    private List<String> stepNames(UUID processTemplateId) {
        return stepTemplateRepository.findAllByProcessTemplateIdOrderByStepOrderAsc(processTemplateId).stream()
                .map(StepTemplateEntity::getName)
                .toList();
    }

    private String extractCustomerId(ProcessExecutionEntity entity) {
        if (entity.getGlobalContext() == null || entity.getGlobalContext().isBlank()) {
            return null;
        }
        try {
            Map<String, Object> context = objectMapper.readValue(
                    entity.getGlobalContext(), new TypeReference<Map<String, Object>>() {
                    });
            Object customerId = context.get("customerId");
            return customerId != null ? customerId.toString() : null;
        } catch (JsonProcessingException ex) {
            log.warn("[PROCESS] Could not parse global context for execution: {}", entity.getId(), ex);
            return null;
        }
    }
}
