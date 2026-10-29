package com.floworchestrator.controlcenter.web.controller;

import com.floworchestrator.controlcenter.model.AssignInterventionRequest;
import com.floworchestrator.controlcenter.model.InterventionResponse;
import com.floworchestrator.controlcenter.model.UpdateInterventionRequest;
import com.floworchestrator.controlcenter.service.FlowInvocationService;
import com.floworchestrator.controlcenter.service.ProcessService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * API REST de intervenciones humanas bajo el prefijo {@code /api/interventions}.
 * Además de persistir la resolución, aprueba o rechaza el flujo en el Orchestrator.
 */
@RestController
@RequestMapping("/api/interventions")
@RequiredArgsConstructor
public class InterventionController {

    private final ProcessService processService;
    private final FlowInvocationService flowInvocationService;

    /**
     * GET /api/interventions lista intervenciones, filtrando opcionalmente por
     * {@code processId} y {@code status}.
     */
    @GetMapping
    public List<InterventionResponse> listInterventions(
            @RequestParam(required = false) UUID processId,
            @RequestParam(required = false) String status) {
        return processService.listInterventions(processId, status);
    }

    /**
     * GET /api/interventions/{id} devuelve una intervención concreta.
     */
    @GetMapping("/{id}")
    public InterventionResponse getIntervention(@PathVariable UUID id) {
        return processService.getIntervention(id);
    }

    /**
     * PUT /api/interventions/{id} actualiza la intervención; al aprobar notifica al
     * Orchestrator para reanudar (o reiniciar si perdió el estado pausado) y al
     * rechazar para cancelar la ejecución.
     */
    @PutMapping("/{id}")
    public ResponseEntity<InterventionResponse> updateIntervention(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateInterventionRequest request) {
        InterventionResponse response = processService.updateIntervention(id, request);

        if (isStatus(request, "APPROVED")) {
            flowInvocationService.resumeOrRestartFlow(
                    UUID.fromString(response.processId()), resumeInput(request.notes()));
        } else if (isStatus(request, "REJECTED")) {
            flowInvocationService.cancelFlow(UUID.fromString(response.processId()));
        }
        return ResponseEntity.ok(response);
    }

    private boolean isStatus(UpdateInterventionRequest request, String status) {
        return request.status() != null && request.status().equalsIgnoreCase(status);
    }

    /**
     * Compone la entrada para reanudar: las notas del operador se envían tras el
     * prefijo {@code APPROVED.}, o solo {@code APPROVED} si no hay notas.
     */
    private String resumeInput(String notes) {
        return notes == null || notes.isBlank() ? "APPROVED" : "APPROVED. " + notes.trim();
    }

    /**
     * PATCH /api/interventions/{id}/assign asigna la intervención a un responsable.
     */
    @PatchMapping("/{id}/assign")
    public ResponseEntity<InterventionResponse> assignIntervention(
            @PathVariable UUID id,
            @Valid @RequestBody AssignInterventionRequest request) {
        return ResponseEntity.ok(processService.assignIntervention(id, request.assignee()));
    }
}
