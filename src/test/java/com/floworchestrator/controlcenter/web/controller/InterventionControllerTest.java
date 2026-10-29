package com.floworchestrator.controlcenter.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.floworchestrator.controlcenter.enums.InterventionStatus;
import com.floworchestrator.controlcenter.error.FlowCode;
import com.floworchestrator.controlcenter.exception.InterventionNotFoundException;
import com.floworchestrator.controlcenter.model.InterventionResponse;
import com.floworchestrator.controlcenter.model.UpdateInterventionRequest;
import com.floworchestrator.controlcenter.service.FlowInvocationService;
import com.floworchestrator.controlcenter.service.ProcessService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InterventionController.class)
class InterventionControllerTest {

    private static final UUID INTERVENTION_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID EXECUTION_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ProcessService processService;

    @MockBean
    private FlowInvocationService flowInvocationService;

    @Test
    @DisplayName("GET /api/interventions should list all interventions")
    void listInterventionsReturnsAll() throws Exception {
        when(processService.listInterventions(null, null)).thenReturn(List.of(response()));

        mockMvc.perform(get("/api/interventions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(INTERVENTION_ID.toString()));
    }

    @Test
    @DisplayName("GET /api/interventions?status=PENDING_REVIEW should filter pending interventions")
    void listInterventionsFiltersPendingReview() throws Exception {
        when(processService.listInterventions(null, "PENDING_REVIEW"))
                .thenReturn(List.of(response(InterventionStatus.PENDING_REVIEW)));

        mockMvc.perform(get("/api/interventions").param("status", "PENDING_REVIEW"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("PENDING_REVIEW"));

        verify(processService).listInterventions(null, "PENDING_REVIEW");
    }

    @Test
    @DisplayName("GET /api/interventions?processId=... should filter by process")
    void listInterventionsFiltersByProcess() throws Exception {
        when(processService.listInterventions(EXECUTION_ID, null))
                .thenReturn(List.of(response()));

        mockMvc.perform(get("/api/interventions").param("processId", EXECUTION_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].processId").value(EXECUTION_ID.toString()));

        verify(processService).listInterventions(EXECUTION_ID, null);
    }

    @Test
    @DisplayName("GET /api/interventions should combine processId and status filters")
    void listInterventionsCombinesProcessAndStatusFilters() throws Exception {
        when(processService.listInterventions(EXECUTION_ID, "PENDING_REVIEW"))
                .thenReturn(List.of(response(InterventionStatus.PENDING_REVIEW)));

        mockMvc.perform(get("/api/interventions")
                        .param("processId", EXECUTION_ID.toString())
                        .param("status", "PENDING_REVIEW"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("PENDING_REVIEW"));

        verify(processService).listInterventions(EXECUTION_ID, "PENDING_REVIEW");
    }

    @Test
    @DisplayName("GET /api/interventions should reject malformed processId")
    void listInterventionsRejectsInvalidProcessId() throws Exception {
        mockMvc.perform(get("/api/interventions").param("processId", "not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.flowCode").value(FlowCode.BAD_REQUEST));

        verify(processService, never()).listInterventions(any(), any());
    }

    @Test
    @DisplayName("GET /api/interventions/{id} should return the intervention")
    void getInterventionReturnsIntervention() throws Exception {
        when(processService.getIntervention(INTERVENTION_ID)).thenReturn(response());

        mockMvc.perform(get("/api/interventions/{id}", INTERVENTION_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stepName").value("validar_rfc"));
    }

    @Test
    @DisplayName("GET /api/interventions/{id} should reject malformed UUIDs")
    void getInterventionRejectsInvalidUuid() throws Exception {
        mockMvc.perform(get("/api/interventions/not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.flowCode").value(FlowCode.BAD_REQUEST));
    }

    @Test
    @DisplayName("PUT /api/interventions/{id} with APPROVED should notify the orchestrator")
    void updateInterventionApprovedNotifiesOrchestrator() throws Exception {
        when(processService.updateIntervention(eq(INTERVENTION_ID), any())).thenReturn(response());

        mockMvc.perform(put("/api/interventions/{id}", INTERVENTION_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new UpdateInterventionRequest("APPROVED", "Cliente proporcionó el RFC"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        verify(flowInvocationService).resumeOrRestartFlow(EXECUTION_ID, "APPROVED. Cliente proporcionó el RFC");
    }

    @Test
    @DisplayName("PUT /api/interventions/{id} without notes should resume with the approval decision")
    void updateInterventionApprovedWithoutNotesResumesWithDecision() throws Exception {
        when(processService.updateIntervention(eq(INTERVENTION_ID), any())).thenReturn(response());

        mockMvc.perform(put("/api/interventions/{id}", INTERVENTION_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"APPROVED\"}"))
                .andExpect(status().isOk());

        verify(flowInvocationService).resumeOrRestartFlow(EXECUTION_ID, "APPROVED");
    }

    @Test
    @DisplayName("PUT /api/interventions/{id} with REJECTED should cancel the flow in the orchestrator")
    void updateInterventionRejectedCancelsFlow() throws Exception {
        when(processService.updateIntervention(eq(INTERVENTION_ID), any()))
                .thenReturn(response(InterventionStatus.REJECTED));

        mockMvc.perform(put("/api/interventions/{id}", INTERVENTION_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"REJECTED\",\"notes\":\"Datos fiscales inválidos\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));

        verify(flowInvocationService).cancelFlow(EXECUTION_ID);
        verify(flowInvocationService, never()).resumeOrRestartFlow(any(), any());
    }

    @Test
    @DisplayName("PUT /api/interventions/{id} without status should not notify the orchestrator")
    void updateInterventionWithoutStatusDoesNotNotifyOrchestrator() throws Exception {
        when(processService.updateIntervention(eq(INTERVENTION_ID), any()))
                .thenReturn(response(InterventionStatus.PENDING_REVIEW));

        mockMvc.perform(put("/api/interventions/{id}", INTERVENTION_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"notes\":\"Pendiente de revisión\"}"))
                .andExpect(status().isOk());

        verify(flowInvocationService, never()).resumeOrRestartFlow(any(), any());
        verify(flowInvocationService, never()).cancelFlow(any());
    }

    @Test
    @DisplayName("PUT /api/interventions/{id} should validate the status value")
    void updateInterventionValidatesStatus() throws Exception {
        mockMvc.perform(put("/api/interventions/{id}", INTERVENTION_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"UNKNOWN\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.flowCode").value(FlowCode.VALIDATION))
                .andExpect(jsonPath("$.details[0].field").value("status"));
    }

    @Test
    @DisplayName("PUT /api/interventions/{id} should return the error contract when not found")
    void updateInterventionReturnsNotFound() throws Exception {
        when(processService.updateIntervention(eq(INTERVENTION_ID), any()))
                .thenThrow(new InterventionNotFoundException(INTERVENTION_ID));

        mockMvc.perform(put("/api/interventions/{id}", INTERVENTION_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"notes\":\"sin estatus\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.flowCode").value(FlowCode.INTERVENTION_NOT_FOUND));
    }

    @Test
    @DisplayName("PATCH /api/interventions/{id}/assign should assign the intervention")
    void assignInterventionReturnsUpdated() throws Exception {
        when(processService.assignIntervention(INTERVENTION_ID, "agent-001")).thenReturn(response());

        mockMvc.perform(patch("/api/interventions/{id}/assign", INTERVENTION_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assignee\":\"agent-001\"}"))
                .andExpect(status().isOk());

        verify(processService).assignIntervention(INTERVENTION_ID, "agent-001");
    }

    @Test
    @DisplayName("PATCH /api/interventions/{id}/assign should validate the assignee")
    void assignInterventionValidatesAssignee() throws Exception {
        mockMvc.perform(patch("/api/interventions/{id}/assign", INTERVENTION_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assignee\":\" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.flowCode").value(FlowCode.VALIDATION))
                .andExpect(jsonPath("$.details[0].field").value("assignee"));
    }

    private InterventionResponse response() {
        return response(InterventionStatus.APPROVED);
    }

    private InterventionResponse response(InterventionStatus status) {
        return new InterventionResponse(
                INTERVENTION_ID.toString(),
                EXECUTION_ID.toString(),
                "validar_rfc",
                3,
                status,
                "SAT timeout",
                "No pudimos validar tu RFC",
                "Verifica tus datos fiscales.",
                "Reintentar validación",
                "warning",
                "trace-1",
                "agent-001",
                "Cliente proporcionó el RFC",
                "Revisar manualmente y continuar el proceso",
                OffsetDateTime.now(),
                OffsetDateTime.now());
    }
}
