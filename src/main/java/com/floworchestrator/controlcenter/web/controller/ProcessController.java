package com.floworchestrator.controlcenter.web.controller;

import com.floworchestrator.controlcenter.model.ProcessExecutionRequest;
import com.floworchestrator.controlcenter.model.ProcessExecutionResponse;
import com.floworchestrator.controlcenter.model.ProcessExecutionStatusRequest;
import com.floworchestrator.controlcenter.model.ProcessExecutionStepResponse;
import com.floworchestrator.controlcenter.model.StepExecutionStatusRequest;
import com.floworchestrator.controlcenter.service.FlowInvocationService;
import com.floworchestrator.controlcenter.service.ProcessService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ProcessController {

    private final ProcessService processService;
    private final FlowInvocationService flowInvocationService;

    /**
     * Crea una nueva ejecución de flujo sin invocar al Orchestrator.
     */
    @PostMapping("/processes")
    public ResponseEntity<ProcessExecutionResponse> createExecution(
            @Valid @RequestBody ProcessExecutionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(processService.createExecution(request));
    }

    /**
     * Crea la ejecución e invoca al Orchestrator para ejecutar los pasos del flujo.
     * El avance se reporta mediante eventos SSE.
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
    public ResponseEntity<ProcessExecutionResponse> updateExecutionStatus(
            @PathVariable UUID id,
            @Valid @RequestBody ProcessExecutionStatusRequest request) {
        return ResponseEntity.ok(processService.updateExecutionStatus(id, request));
    }

    @PostMapping("/processes/{id}/retry")
    public ResponseEntity<ProcessExecutionResponse> retryProcess(
            @PathVariable UUID id,
            @RequestParam(required = false) String userInput) {
        return ResponseEntity.ok(flowInvocationService.retryProcess(id, userInput));
    }

    @PutMapping("/processes/{id}/steps/{stepOrder}")
    public ResponseEntity<ProcessExecutionStepResponse> updateStepExecution(
            @PathVariable UUID id,
            @PathVariable int stepOrder,
            @Valid @RequestBody StepExecutionStatusRequest request) {
        return ResponseEntity.ok(processService.updateStepExecution(id, stepOrder, request));
    }

    @PostMapping("/processes/{id}/steps/{stepOrder}/retry")
    public ResponseEntity<ProcessExecutionResponse> retryStep(
            @PathVariable UUID id,
            @PathVariable int stepOrder) {
        flowInvocationService.retryStep(id, stepOrder);
        return ResponseEntity.ok(processService.getExecution(id));
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
     */
    @GetMapping("/processes/health")
    public String health() {
        return "Control Center OK";
    }
}
