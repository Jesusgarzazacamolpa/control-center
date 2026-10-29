package com.floworchestrator.controlcenter.web.controller;

import com.floworchestrator.controlcenter.model.ProcessExecutionPage;
import com.floworchestrator.controlcenter.model.ProcessExecutionRequest;
import com.floworchestrator.controlcenter.model.ProcessExecutionResponse;
import com.floworchestrator.controlcenter.model.ProcessExecutionStatusRequest;
import com.floworchestrator.controlcenter.model.ProcessExecutionStepResponse;
import com.floworchestrator.controlcenter.model.StepExecutionStatusRequest;
import com.floworchestrator.controlcenter.service.FlowInvocationService;
import com.floworchestrator.controlcenter.service.ProcessService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * API REST de ejecuciones y plantillas de proceso bajo el prefijo {@code /api}.
 * Expone creación, consulta, paginación, cambio de estatus y reintentos.
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Validated
public class ProcessController {

    private final ProcessService processService;
    private final FlowInvocationService flowInvocationService;

    /**
     * POST /api/processes crea una ejecución en PENDING sin invocar al Orchestrator.
     */
    @PostMapping("/processes")
    public ResponseEntity<ProcessExecutionResponse> createExecution(
            @Valid @RequestBody ProcessExecutionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(processService.createExecution(request));
    }

    /**
     * POST /api/processes/start crea la ejecución e inicia el flujo en el
     * Orchestrator; el avance llega después por SSE.
     */
    @PostMapping("/processes/start")
    public ResponseEntity<ProcessExecutionResponse> startExecution(
            @Valid @RequestBody ProcessExecutionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(flowInvocationService.startProcess(request));
    }

    /**
     * GET /api/process-templates/default devuelve el id de la plantilla por defecto.
     */
    @GetMapping("/process-templates/default")
    public Map<String, String> getDefaultProcessTemplate() {
        return Map.of("id", processService.getDefaultProcessTemplateId());
    }

    /**
     * GET /api/process-templates/by-name?name= busca el id de una plantilla activa
     * por nombre.
     */
    @GetMapping("/process-templates/by-name")
    public Map<String, String> getProcessTemplateByName(@RequestParam String name) {
        return Map.of("id", processService.getActiveProcessTemplateId(name));
    }

    /**
     * PATCH /api/processes/{id}/status actualiza estatus y mensajes de error de la
     * ejecución.
     */
    @PatchMapping("/processes/{id}/status")
    public ResponseEntity<ProcessExecutionResponse> updateExecutionStatus(
            @PathVariable UUID id,
            @Valid @RequestBody ProcessExecutionStatusRequest request) {
        return ResponseEntity.ok(processService.updateExecutionStatus(id, request));
    }

    /**
     * POST /api/processes/{id}/retry reintenta una ejecución pausada o fallida;
     * si no hay estado en el Orchestrator la reinicia desde el principio.
     */
    @PostMapping("/processes/{id}/retry")
    public ResponseEntity<ProcessExecutionResponse> retryProcess(
            @PathVariable UUID id,
            @RequestParam(required = false) String userInput) {
        return ResponseEntity.ok(flowInvocationService.retryProcess(id, userInput));
    }

    /**
     * PUT /api/processes/{id}/steps/{stepOrder} actualiza el estatus de un paso y
     * sincroniza el estado del proceso.
     */
    @PutMapping("/processes/{id}/steps/{stepOrder}")
    public ResponseEntity<ProcessExecutionStepResponse> updateStepExecution(
            @PathVariable UUID id,
            @PathVariable int stepOrder,
            @Valid @RequestBody StepExecutionStatusRequest request) {
        return ResponseEntity.ok(processService.updateStepExecution(id, stepOrder, request));
    }

    /**
     * POST /api/processes/{id}/steps/{stepOrder}/retry marca el paso para reintento
     * en el Orchestrator y devuelve la ejecución actualizada.
     */
    @PostMapping("/processes/{id}/steps/{stepOrder}/retry")
    public ResponseEntity<ProcessExecutionResponse> retryStep(
            @PathVariable UUID id,
            @PathVariable int stepOrder) {
        flowInvocationService.retryStep(id, stepOrder);
        return ResponseEntity.ok(processService.getExecution(id));
    }

    /**
     * GET /api/processes/{id}/steps lista los pasos ejecutados del proceso.
     */
    @GetMapping("/processes/{id}/steps")
    public List<ProcessExecutionStepResponse> getExecutionSteps(@PathVariable UUID id) {
        return processService.getExecutionSteps(id);
    }

    /**
     * GET /api/processes lista una página de ejecuciones (page desde 0, size 1..100)
     * ordenada por inicio descendente.
     */
    @GetMapping("/processes")
    public ProcessExecutionPage listExecutions(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size) {
        return processService.listExecutions(page, size);
    }

    /**
     * GET /api/processes/{id} devuelve una ejecución concreta.
     */
    @GetMapping("/processes/{id}")
    public ProcessExecutionResponse getExecution(@PathVariable UUID id) {
        return processService.getExecution(id);
    }

    /**
     * GET /api/processes/health endpoint de salud simple del Control Center.
     */
    @GetMapping("/processes/health")
    public String health() {
        return "Control Center OK";
    }
}
