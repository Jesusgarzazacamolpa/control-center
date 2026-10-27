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

@RestController
@RequestMapping("/api/interventions")
@RequiredArgsConstructor
public class InterventionController {

    private final ProcessService processService;
    private final FlowInvocationService flowInvocationService;

    @GetMapping
    public List<InterventionResponse> listInterventions(
            @RequestParam(required = false) String status) {
        if (status == null || status.isBlank()) {
            return processService.listInterventions();
        }
        return processService.listInterventionsByStatus(status);
    }

    @GetMapping("/{id}")
    public InterventionResponse getIntervention(@PathVariable UUID id) {
        return processService.getIntervention(id);
    }

    @PutMapping("/{id}")
    public ResponseEntity<InterventionResponse> updateIntervention(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateInterventionRequest request) {
        InterventionResponse response = processService.updateIntervention(id, request);

        if (request.status() != null && request.status().equalsIgnoreCase("APPROVED")) {
            flowInvocationService.resumeFlow(UUID.fromString(response.processId()), null);
        }
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{id}/assign")
    public ResponseEntity<InterventionResponse> assignIntervention(
            @PathVariable UUID id,
            @Valid @RequestBody AssignInterventionRequest request) {
        return ResponseEntity.ok(processService.assignIntervention(id, request.assignee()));
    }
}
