package com.floworchestrator.controlcenter.web.controller;

import com.floworchestrator.controlcenter.model.ProcessExecutionRequest;
import com.floworchestrator.controlcenter.model.ProcessExecutionResponse;
import com.floworchestrator.controlcenter.model.ProcessExecutionStepResponse;
import com.floworchestrator.controlcenter.model.ProcessExecutionStatusRequest;
import com.floworchestrator.controlcenter.model.StepExecutionStatusRequest;
import com.floworchestrator.controlcenter.service.FlowInvocationService;
import com.floworchestrator.controlcenter.service.ProcessService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ProcessController {

    private final ProcessService processService;
    private final FlowInvocationService flowInvocationService;

    /**
     * Crea una nueva ejecución de flujo a partir de la solicitud proporcionada.
     *
     * @param request Objeto que contiene los datos de la ejecución del flujo, validado con anotaciones JSR-303.
     * @return {@link ProcessExecutionResponse} con el resultado de la creación de la ejecución, respuesta HTTP 201 Created.
     */
    @PostMapping("/processes")
    public ResponseEntity<ProcessExecutionResponse> createExecution(@Valid @RequestBody ProcessExecutionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(processService.createExecution(request));
    }

    /**
     * Crea la ejecución del proceso e invoca al Orchestrator para ejecutar
     * los pasos de los MCPs. El estatus de cada paso se reporta mediante
     * eventos SSE y se registra en el bitácora del Control Center.
     *
     * @param request Objeto que contiene los datos de la ejecución del flujo.
     * @return {@link ProcessExecutionResponse} con la ejecución creada (estado PENDING).
     */
    @PostMapping("/processes/start")
    public ResponseEntity<ProcessExecutionResponse> startExecution(
            @Valid @RequestBody ProcessExecutionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(flowInvocationService.startProcess(request));
    }

    @GetMapping("/process-templates/default")
    public Map<String, String> getDefaultProcessTemplate() {
        return Map.of("id", processService.getDefaultProcessTemplateId());
    }

    @GetMapping("/process-templates/by-name")
    public Map<String, String> getProcessTemplateByName(@RequestParam String name) {
        return Map.of("id", processService.getActiveProcessTemplateId(name));
    }

    @PatchMapping("/processes/{id}/status")
    public ProcessExecutionResponse updateExecutionStatus(
            @PathVariable UUID id,
            @Valid @RequestBody ProcessExecutionStatusRequest request) {
        return processService.updateExecutionStatus(
                id,
                request.getStatus(),
                request.getCurrentStep(),
                request.getErrorMessage());
    }

    @PutMapping("/processes/{id}/steps/{stepOrder}")
    public ProcessExecutionStepResponse updateStepExecution(
            @PathVariable UUID id,
            @PathVariable int stepOrder,
            @Valid @RequestBody StepExecutionStatusRequest request) {
        return processService.updateStepExecution(id, stepOrder, request.getStatus(), request.getErrorMessage());
    }

    @GetMapping("/processes/{id}/steps")
    public List<ProcessExecutionStepResponse> getExecutionSteps(@PathVariable UUID id) {
        return processService.getExecutionSteps(id);
    }

    @GetMapping("/processes")
    public List<ProcessExecutionResponse> listExecutions() {
        return processService.listExecutions();
    }

    @GetMapping("/processes/{id}")
    public ProcessExecutionResponse getExecution(@PathVariable UUID id) {
        return processService.getExecution(id);
    }

    /**
     * Endpoint de salud del Control Center.
     *
     * @return Cadena de texto indicando que el servicio está activo.
     */
    @GetMapping("/processes/health")
    public String health() {
        return "Control Center OK";
    }
}
