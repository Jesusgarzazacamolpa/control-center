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
import com.floworchestrator.controlcenter.model.ProcessExecutionPage;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Servicio central del Control Center: persiste ejecuciones, pasos e
 * intervenciones y aplica las transiciones de estado derivadas de la API y de
 * los eventos SSE, dentro de transacciones.
 */
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

    /**
     * Devuelve el id de la plantilla activa "Compra de vehiculo", la usada por defecto.
     */
    @Transactional(readOnly = true)
    public String getDefaultProcessTemplateId() {
        return getActiveProcessTemplateId(DEFAULT_TEMPLATE_NAME);
    }

    /**
     * Devuelve el id de la primera plantilla activa con ese nombre.
     *
     * @throws ProcessTemplateNotFoundException si no existe ninguna activa con ese nombre.
     */
    @Transactional(readOnly = true)
    public String getActiveProcessTemplateId(String name) {
        return processTemplateRepository.findFirstByNameAndActiveTrue(name)
                .map(template -> template.getId().toString())
                .orElseThrow(() -> new ProcessTemplateNotFoundException(name));
    }

    /**
     * Crea una nueva ejecución de flujo en estado PENDING con el contexto del cliente.
     * Solo persiste; no invoca al Orchestrator.
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

    /**
     * Sobrescribe estatus y mensajes de error del proceso, y fija {@code completedAt}
     * cuando el estatus es terminal (en otro caso lo limpia).
     */
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

    /**
     * Actualiza el paso (lo crea si es su primera ejecución) y sincroniza el estado
     * del proceso mediante las transiciones de {@code applyStepStatus}.
     *
     * @throws StepNotFoundException si la plantilla no define ese número de paso.
     */
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
        // Los mensajes de usuario del paso viven en el paso: copiarlos al proceso
        // hacía que una intervención mostrara "Error inesperado" en toda la pantalla.
        applyTraceId(process);
        process.setLastUpdatedAt(now);

        repository.save(process);
        StepExecutionEntity saved = stepExecutionRepository.save(step);
        return toStepResponse(saved, template);
    }

    /**
     * Lista una página de ejecuciones ordenada por fecha de inicio descendente,
     * resolviendo los nombres de pasos en una sola consulta por lote.
     */
    @Transactional(readOnly = true)
    public ProcessExecutionPage listExecutions(int page, int size) {
        Page<ProcessExecutionEntity> executions = repository.findAll(
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "startedAt")));
        Map<UUID, List<String>> stepsByTemplate = stepNamesByTemplate(executions.getContent());
        List<ProcessExecutionResponse> content = executions.getContent().stream()
                .map(entity -> toResponse(entity, stepsByTemplate.getOrDefault(
                        entity.getProcessTemplateId(), List.of())))
                .toList();
        return new ProcessExecutionPage(content, executions.getNumber(), executions.getSize(),
                executions.getTotalElements(), executions.getTotalPages());
    }

    /**
     * Agrupa por plantilla los nombres de pasos de las ejecuciones de la página,
     * evitando una consulta por fila.
     */
    private Map<UUID, List<String>> stepNamesByTemplate(List<ProcessExecutionEntity> executions) {
        Set<UUID> templateIds = executions.stream()
                .map(ProcessExecutionEntity::getProcessTemplateId)
                .collect(Collectors.toSet());
        if (templateIds.isEmpty()) {
            return Map.of();
        }
        return stepTemplateRepository.findAllByProcessTemplateIdInOrderByStepOrderAsc(templateIds).stream()
                .collect(Collectors.groupingBy(StepTemplateEntity::getProcessTemplateId,
                        Collectors.mapping(StepTemplateEntity::getName, Collectors.toList())));
    }

    /**
     * Devuelve la ejecución con los nombres de pasos de su plantilla.
     */
    @Transactional(readOnly = true)
    public ProcessExecutionResponse getExecution(UUID executionId) {
        return toResponse(getExecutionEntity(executionId));
    }

    /**
     * Reconstruye la solicitud original (plantilla, cliente, vehículo y agente)
     * para poder reiniciar el flujo desde el principio.
     */
    @Transactional(readOnly = true)
    public ProcessExecutionRequest getExecutionRequest(UUID executionId) {
        ProcessExecutionEntity entity = getExecutionEntity(executionId);
        return new ProcessExecutionRequest(
                entity.getProcessTemplateId().toString(),
                extractCustomerId(entity),
                entity.getVehicleId(),
                entity.getSalesAgentId());
    }

    /**
     * Lista los pasos ejecutados de un proceso en orden de inicio, con nombre y
     * orden tomados de la plantilla.
     */
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

    /**
     * Registra una intervención en PENDING_REVIEW con los mensajes de error del paso
     * y actualiza la última modificación del proceso.
     */
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

        log.info("[INTERVENTION] Intervención creada: id={}, processId={}, step={}",
                saved.getId(), executionId, request.stepName());
        return toInterventionResponse(saved);
    }

    /**
     * Lista intervenciones filtrando opcionalmente por proceso y estatus
     * (el estatus se interpreta sin distinguir mayúsculas).
     */
    @Transactional(readOnly = true)
    public List<InterventionResponse> listInterventions(UUID processId, String rawStatus) {
        InterventionStatus status = parseOptionalStatus(rawStatus);
        return findInterventions(processId, status).stream()
                .map(this::toInterventionResponse)
                .toList();
    }

    /**
     * Elige la consulta según los filtros presentes; sin filtros usa {@code findAll()},
     * que no garantiza el orden por fecha.
     */
    private List<InterventionEntity> findInterventions(UUID processId, InterventionStatus status) {
        if (processId != null && status != null) {
            return interventionRepository
                    .findByProcessExecutionIdAndStatusOrderByCreatedAtDesc(processId, status);
        }
        if (processId != null) {
            return interventionRepository.findByProcessExecutionIdOrderByCreatedAtDesc(processId);
        }
        if (status != null) {
            return interventionRepository.findByStatusOrderByCreatedAtDesc(status);
        }
        return interventionRepository.findAll();
    }

    private InterventionStatus parseOptionalStatus(String rawStatus) {
        if (rawStatus == null || rawStatus.isBlank()) {
            return null;
        }
        return parseInterventionStatus(rawStatus.trim().toUpperCase(Locale.ROOT));
    }

    /**
     * Devuelve una intervención por id.
     *
     * @throws InterventionNotFoundException si no existe.
     */
    @Transactional(readOnly = true)
    public InterventionResponse getIntervention(UUID interventionId) {
        return toInterventionResponse(getInterventionEntity(interventionId));
    }

    /**
     * Actualiza estatus y/o notas de una intervención. Aprobar reanuda la ejecución
     * como IN_PROGRESS; rechazar la deja CANCELLED con mensaje de error.
     */
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
        log.info("[INTERVENTION] Intervención actualizada: id={}, status={}", saved.getId(), saved.getStatus());
        return toInterventionResponse(saved);
    }

    /**
     * Asigna la intervención a un responsable sin cambiar su estatus.
     */
    @Transactional
    public InterventionResponse assignIntervention(UUID interventionId, String assignee) {
        InterventionEntity entity = getInterventionEntity(interventionId);
        entity.setAssignedTo(assignee);
        entity.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));

        InterventionEntity saved = interventionRepository.save(entity);
        log.info("[INTERVENTION] Intervención asignada: id={}, assignee={}", saved.getId(), assignee);
        return toInterventionResponse(saved);
    }

    /**
     * Pasa a IN_PROGRESS una ejecución pausada o fallida y limpia los mensajes de
     * error; si el estado no es reintentable lo omite en lugar de fallar. Lo usa el
     * evento SSE {@code FLOW_RESUMED}.
     */
    @Transactional
    public void resumeExecution(UUID executionId) {
        ProcessExecutionEntity process = getExecutionEntity(executionId);
        if (!process.getStatus().isRetryable()) {
            log.debug("[INTERVENTION] La ejecución no está pausada ni fallida; se omite la reanudación: id={}, status={}",
                    executionId, process.getStatus());
            return;
        }
        process.setStatus(ProcessExecutionStatus.IN_PROGRESS);
        process.setErrorMessage(null);
        process.setUserErrorTitle(null);
        process.setUserErrorMessage(null);
        process.setUserErrorActionLabel(null);
        process.setUserErrorStatusType(null);
        process.setCompletedAt(null);
        process.setLastUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        repository.save(process);
        log.info("[INTERVENTION] Ejecución reanudada mediante evento SSE: id={}", executionId);
    }

    /**
     * Aplica la transición del proceso según el nuevo estatus de la intervención:
     * aprobar reanuda, rechazar cancela.
     */
    private void applyInterventionStatus(InterventionEntity entity, String rawStatus) {
        InterventionStatus status = parseInterventionStatus(rawStatus.trim().toUpperCase(Locale.ROOT));
        entity.setStatus(status);
        switch (status) {
            case APPROVED -> processExecutionAfterResume(entity.getProcessExecutionId(), entity.getStepName());
            case REJECTED -> processExecutionAfterRejection(entity.getProcessExecutionId(), entity.getStepName());
            default -> {
            }
        }
    }

    private InterventionStatus parseInterventionStatus(String status) {
        try {
            return InterventionStatus.valueOf(status);
        } catch (IllegalArgumentException ex) {
            throw new InvalidInterventionStatusException(status);
        }
    }

    /**
     * Deja la ejecución IN_PROGRESS en el paso intervenido y limpia los errores
     * visibles al usuario.
     */
    private void processExecutionAfterResume(UUID executionId, String currentStep) {
        ProcessExecutionEntity process = getExecutionEntity(executionId);
        process.setStatus(ProcessExecutionStatus.IN_PROGRESS);
        process.setCurrentStep(currentStep);
        process.setErrorMessage(null);
        process.setUserErrorTitle(null);
        process.setUserErrorMessage(null);
        process.setUserErrorActionLabel(null);
        process.setUserErrorStatusType(null);
        process.setLastUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        repository.save(process);
        log.info("[INTERVENTION] Ejecución del proceso reanudada: id={}", executionId);
    }

    /**
     * Deja la ejecución CANCELLED con mensajes de rechazo listos para la UI.
     */
    private void processExecutionAfterRejection(UUID executionId, String currentStep) {
        ProcessExecutionEntity process = getExecutionEntity(executionId);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        process.setStatus(ProcessExecutionStatus.CANCELLED);
        process.setCurrentStep(currentStep);
        process.setErrorMessage("El operador rechazó la intervención.");
        process.setUserErrorTitle("Proceso cancelado");
        process.setUserErrorMessage("La autorización fue rechazada, por lo que el proceso no puede continuar.");
        process.setUserErrorActionLabel("Iniciar un nuevo proceso");
        process.setUserErrorStatusType("error");
        process.setCompletedAt(now);
        process.setLastUpdatedAt(now);
        repository.save(process);
        log.info("[INTERVENTION] Ejecución del proceso cancelada por rechazo: id={}", executionId);
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
            log.warn("[PROCESS] No se pudo serializar el contexto global para customerId={}", customerId, ex);
            return "{}";
        }
    }

    /**
     * Reglas de transición del proceso ante cada estatus de paso; el paso PENDING
     * solo se registra en el log.
     */
    private void applyStepStatus(ProcessExecutionEntity process, StepExecutionEntity step,
                                 StepTemplateEntity template, StepExecutionStatusRequest request,
                                 OffsetDateTime now) {
        switch (request.status()) {
            case RUNNING -> applyRunningStatus(process, step, template, now);
            case COMPLETED -> applyCompletedStatus(process, step, now);
            case FAILED -> applyFailedStatus(process, step, template, request.errorMessage(), now);
            case REQUIRES_INTERVENTION -> applyInterventionStatus(process, step, template, request.errorMessage());
            case PENDING -> log.debug("[PROCESS] Paso marcado como PENDING: stepOrder={}", template.getStepOrder());
        }
    }

    /**
     * Incrementa el contador de reintentos solo si el paso estaba FAILED y deja el
     * proceso IN_PROGRESS en ese paso.
     */
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
        process.setUserErrorTitle(null);
        process.setUserErrorMessage(null);
        process.setUserErrorActionLabel(null);
        process.setUserErrorStatusType(null);
        process.setCompletedAt(null);
    }

    /**
     * Completa el paso y limpia el paso actual, pero no marca el proceso COMPLETED:
     * eso ocurre con el evento FLOW_COMPLETED.
     */
    private void applyCompletedStatus(ProcessExecutionEntity process, StepExecutionEntity step,
                                      OffsetDateTime now) {
        step.setCompletedAt(now);
        process.setCurrentStep(null);
        process.setErrorMessage(null);
    }

    /**
     * Marca paso y proceso como FAILED y fija la fecha de finalización de ambos.
     */
    private void applyFailedStatus(ProcessExecutionEntity process, StepExecutionEntity step,
                                   StepTemplateEntity template, String errorMessage, OffsetDateTime now) {
        step.setCompletedAt(now);
        process.setStatus(ProcessExecutionStatus.FAILED);
        process.setCurrentStep(template.getName());
        process.setErrorMessage(errorMessage);
        process.setCompletedAt(now);
    }

    /**
     * Pausa el proceso para intervención humana en el paso indicado.
     */
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
        return toResponse(entity, stepNames(entity.getProcessTemplateId()));
    }

    private ProcessExecutionResponse toResponse(ProcessExecutionEntity entity, List<String> steps) {
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
                steps);
    }

    private List<String> stepNames(UUID processTemplateId) {
        return stepTemplateRepository.findAllByProcessTemplateIdOrderByStepOrderAsc(processTemplateId).stream()
                .map(StepTemplateEntity::getName)
                .toList();
    }

    /**
     * Extrae {@code customerId} del jsonb; si el contenido no es válido devuelve
     * {@code null} y solo deja un warning.
     */
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
            log.warn("[PROCESS] No se pudo analizar el contexto global de la ejecución: {}", entity.getId(), ex);
            return null;
        }
    }
}
