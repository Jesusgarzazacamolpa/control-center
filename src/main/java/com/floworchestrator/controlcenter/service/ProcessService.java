package com.floworchestrator.controlcenter.service;

import com.floworchestrator.controlcenter.model.ProcessExecutionRequest;
import com.floworchestrator.controlcenter.model.ProcessExecutionResponse;
import com.floworchestrator.controlcenter.model.ProcessExecutionStepResponse;
import com.floworchestrator.controlcenter.enums.ProcessExecutionStatus;
import com.floworchestrator.controlcenter.enums.StepExecutionStatus;
import com.floworchestrator.controlcenter.entity.ProcessExecutionEntity;
import com.floworchestrator.controlcenter.entity.ProcessTemplateEntity;
import com.floworchestrator.controlcenter.entity.StepExecutionEntity;
import com.floworchestrator.controlcenter.entity.StepTemplateEntity;
import com.floworchestrator.controlcenter.repository.ProcessTemplateRepository;
import com.floworchestrator.controlcenter.repository.ProcessExecutionRepository;
import com.floworchestrator.controlcenter.repository.StepExecutionRepository;
import com.floworchestrator.controlcenter.repository.StepTemplateRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProcessService {

    private final ProcessExecutionRepository repository;
    private final ProcessTemplateRepository processTemplateRepository;
    private final StepTemplateRepository stepTemplateRepository;
    private final StepExecutionRepository stepExecutionRepository;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public String getDefaultProcessTemplateId() {
        return getActiveProcessTemplateId("Compra de vehiculo");
    }

    @Transactional(readOnly = true)
    public String getActiveProcessTemplateId(String name) {
        return processTemplateRepository.findFirstByNameAndActiveTrue(name)
                .map(template -> template.getId().toString())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Active process template not found"));
    }

    /**
     * Crea una nueva ejecución de flujo a partir de la solicitud proporcionada.
     * <p>
     * El método inicializa una nueva entidad de ejecución con el ID de la plantilla de flujo,
    * el estado PENDING, el contexto global del cliente y las marcas de tiempo actual.
     * Persiste la entidad y construye una respuesta con los pasos predefinidos del flujo.
     *
     * @param request Objeto que contiene los datos de la ejecución del flujo.
     * @return {@link ProcessExecutionResponse} con la información de la ejecución creada.
     */
    @Transactional
    public ProcessExecutionResponse createExecution(ProcessExecutionRequest request) {
        ProcessExecutionEntity entity = new ProcessExecutionEntity();
        entity.setProcessTemplateId(request.getProcessTemplateId());
        entity.setStatus(ProcessExecutionStatus.PENDING);
        entity.setGlobalContext(buildGlobalContext(request.getCustomerId()));
        entity.setStartedAt(OffsetDateTime.now(ZoneOffset.UTC));
        entity.setLastUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));

        ProcessExecutionEntity saved = repository.save(entity);

        ProcessExecutionResponse response = new ProcessExecutionResponse();
        response.setId(saved.getId().toString());
        response.setProcessTemplateId(saved.getProcessTemplateId());
        response.setCustomerId(request.getCustomerId());
        response.setStatus(saved.getStatus());
        response.setCurrentStep(saved.getCurrentStep());
        response.setErrorMessage(saved.getErrorMessage());
        response.setSteps(stepTemplateRepository
            .findAllByProcessTemplateIdOrderByStepOrderAsc(saved.getProcessTemplateIdUuid())
            .stream()
            .map(StepTemplateEntity::getName)
            .toList());
        return response;
    }

    @Transactional
    public ProcessExecutionResponse updateExecutionStatus(
            UUID executionId,
            ProcessExecutionStatus status,
            String currentStep,
            String errorMessage) {
        ProcessExecutionEntity entity = repository.findById(executionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Process execution not found"));

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        entity.setStatus(status);
        entity.setCurrentStep(currentStep);
        entity.setErrorMessage(errorMessage);
        entity.setLastUpdatedAt(now);

        if (status == ProcessExecutionStatus.COMPLETED
                || status == ProcessExecutionStatus.FAILED
                || status == ProcessExecutionStatus.CANCELLED) {
            entity.setCompletedAt(now);
        } else {
            entity.setCompletedAt(null);
        }

        ProcessExecutionEntity saved = repository.save(entity);
        ProcessExecutionResponse response = new ProcessExecutionResponse();
        response.setId(saved.getId().toString());
        response.setProcessTemplateId(saved.getProcessTemplateId());
        response.setStatus(saved.getStatus());
        response.setCurrentStep(saved.getCurrentStep());
        response.setErrorMessage(saved.getErrorMessage());
        return response;
    }

    @Transactional
    public ProcessExecutionStepResponse updateStepExecution(
            UUID executionId,
            int stepOrder,
            StepExecutionStatus status,
            String errorMessage) {
        ProcessExecutionEntity process = repository.findById(executionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Process execution not found"));
        StepTemplateEntity template = stepTemplateRepository
                .findByProcessTemplateIdAndStepOrder(process.getProcessTemplateIdUuid(), stepOrder)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Step template not found"));

        StepExecutionEntity step = stepExecutionRepository
                .findByProcessExecutionIdAndStepTemplateId(executionId, template.getId())
                .orElseGet(() -> {
                    StepExecutionEntity newStep = new StepExecutionEntity();
                    newStep.setProcessExecutionId(executionId);
                    newStep.setStepTemplateId(template.getId());
                    newStep.setRetryCount(0);
                    return newStep;
                });

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        applyStepStatus(process, step, template, status, errorMessage, now);

        step.setStatus(status);
        step.setErrorMessage(errorMessage);
        process.setLastUpdatedAt(now);
        repository.save(process);
        StepExecutionEntity saved = stepExecutionRepository.save(step);
        return toStepResponse(saved, template);
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
                                  StepTemplateEntity template, StepExecutionStatus status,
                                  String errorMessage, OffsetDateTime now) {
        switch (status) {
            case RUNNING -> applyRunningStatus(process, step, template, now);
            case COMPLETED -> applyCompletedStatus(process, step, now);
            case FAILED -> applyFailedStatus(process, step, template, errorMessage, now);
            case REQUIRES_INTERVENTION -> applyInterventionStatus(process, step, template, errorMessage);
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

    @Transactional(readOnly = true)
    public List<ProcessExecutionResponse> listExecutions() {
        return repository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ProcessExecutionResponse getExecution(UUID executionId) {
        ProcessExecutionEntity entity = repository.findById(executionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Process execution not found"));
        return toResponse(entity);
    }

    @Transactional(readOnly = true)
    public List<ProcessExecutionStepResponse> getExecutionSteps(UUID executionId) {
        if (!repository.existsById(executionId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Process execution not found");
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

    private ProcessExecutionStepResponse toStepResponse(StepExecutionEntity step, StepTemplateEntity template) {
        ProcessExecutionStepResponse response = new ProcessExecutionStepResponse();
        response.setId(step.getId().toString());
        response.setStepOrder(template.getStepOrder());
        response.setName(template.getName());
        response.setStatus(step.getStatus());
        response.setRetryCount(step.getRetryCount());
        response.setErrorMessage(step.getErrorMessage());
        response.setStartedAt(step.getStartedAt());
        response.setCompletedAt(step.getCompletedAt());
        return response;
    }

    private ProcessExecutionResponse toResponse(ProcessExecutionEntity entity) {
        ProcessExecutionResponse response = new ProcessExecutionResponse();
        response.setId(entity.getId().toString());
        response.setProcessTemplateId(entity.getProcessTemplateId());
        response.setCustomerId(extractCustomerId(entity));
        response.setStatus(entity.getStatus());
        response.setCurrentStep(entity.getCurrentStep());
        response.setErrorMessage(entity.getErrorMessage());
        response.setSteps(stepTemplateRepository
                .findAllByProcessTemplateIdOrderByStepOrderAsc(entity.getProcessTemplateIdUuid())
                .stream()
                .map(StepTemplateEntity::getName)
                .toList());
        return response;
    }

    private String extractCustomerId(ProcessExecutionEntity entity) {
        if (entity.getGlobalContext() == null || entity.getGlobalContext().isBlank()) {
            return null;
        }
        try {
            Map<String, Object> context = objectMapper.readValue(entity.getGlobalContext(), Map.class);
            Object customerId = context.get("customerId");
            return customerId != null ? customerId.toString() : null;
        } catch (JsonProcessingException ex) {
            log.warn("[PROCESS] Could not parse global context for execution: {}", entity.getId(), ex);
            return null;
        }
    }
}
