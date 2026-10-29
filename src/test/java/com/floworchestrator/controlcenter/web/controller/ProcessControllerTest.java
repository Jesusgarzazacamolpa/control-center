package com.floworchestrator.controlcenter.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.floworchestrator.controlcenter.enums.ProcessExecutionStatus;
import com.floworchestrator.controlcenter.enums.StepExecutionStatus;
import com.floworchestrator.controlcenter.error.FlowCode;
import com.floworchestrator.controlcenter.exception.InvalidProcessStateException;
import com.floworchestrator.controlcenter.exception.OrchestratorUnavailableException;
import com.floworchestrator.controlcenter.exception.ProcessNotFoundException;
import com.floworchestrator.controlcenter.model.ProcessExecutionPage;
import com.floworchestrator.controlcenter.model.ProcessExecutionRequest;
import com.floworchestrator.controlcenter.model.ProcessExecutionResponse;
import com.floworchestrator.controlcenter.model.ProcessExecutionStatusRequest;
import com.floworchestrator.controlcenter.model.ProcessExecutionStepResponse;
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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProcessController.class)
class ProcessControllerTest {

    private static final UUID EXECUTION_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID TEMPLATE_ID = UUID.fromString("558e6497-786f-4e23-8c97-3d681864225b");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ProcessService processService;

    @MockBean
    private FlowInvocationService flowInvocationService;

    @Test
    @DisplayName("POST /api/processes should return the created execution")
    void createExecutionReturnsCreated() throws Exception {
        when(processService.createExecution(any())).thenReturn(response(ProcessExecutionStatus.PENDING));

        mockMvc.perform(post("/api/processes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(EXECUTION_ID.toString()))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.customerId").value("customer-004"))
                .andExpect(jsonPath("$.steps[0]").value("Consultar vehículo"));
    }

    @Test
    @DisplayName("POST /api/processes/start should delegate to the flow invocation use case")
    void startExecutionReturnsCreated() throws Exception {
        when(flowInvocationService.startProcess(any())).thenReturn(response(ProcessExecutionStatus.PENDING));

        mockMvc.perform(post("/api/processes/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"));

        verify(flowInvocationService).startProcess(any(ProcessExecutionRequest.class));
    }

    @Test
    @DisplayName("POST /api/processes/start should return validation flow code for missing customer")
    void startExecutionValidatesPayload() throws Exception {
        ProcessExecutionRequest invalid = new ProcessExecutionRequest(TEMPLATE_ID.toString(), " ", null, null);

        mockMvc.perform(post("/api/processes/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.flowCode").value(FlowCode.VALIDATION))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.startsWith("Revisa los datos ingresados")))
                .andExpect(jsonPath("$.details[0].field").value("customerId"))
                .andExpect(jsonPath("$.path").value("/api/processes/start"));

        verify(flowInvocationService, never()).startProcess(any());
    }

    @Test
    @DisplayName("POST /api/processes/start should validate the template UUID format")
    void startExecutionValidatesTemplateUuid() throws Exception {
        ProcessExecutionRequest invalid = new ProcessExecutionRequest("not-a-uuid", "customer-1", null, null);

        mockMvc.perform(post("/api/processes/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.flowCode").value(FlowCode.VALIDATION))
                .andExpect(jsonPath("$.details[0].field").value("processTemplateId"));
    }

    @Test
    @DisplayName("GET /api/processes/{id} should serialize the execution")
    void getExecutionReturnsExecution() throws Exception {
        when(processService.getExecution(EXECUTION_ID)).thenReturn(response(ProcessExecutionStatus.IN_PROGRESS));

        mockMvc.perform(get("/api/processes/{id}", EXECUTION_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(EXECUTION_ID.toString()))
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.traceId").value("trace-1"));
    }

    @Test
    @DisplayName("GET /api/processes/{id} should return the error contract when not found")
    void getExecutionReturnsErrorContract() throws Exception {
        when(processService.getExecution(EXECUTION_ID)).thenThrow(new ProcessNotFoundException(EXECUTION_ID));

        mockMvc.perform(get("/api/processes/{id}", EXECUTION_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.flowCode").value(FlowCode.PROCESS_NOT_FOUND))
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("No encontramos el proceso")))
                .andExpect(jsonPath("$.path").value("/api/processes/" + EXECUTION_ID))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.stackTrace").doesNotExist());
    }

    @Test
    @DisplayName("GET /api/processes/{id} should return 400 for a malformed UUID")
    void getExecutionRejectsInvalidUuid() throws Exception {
        mockMvc.perform(get("/api/processes/not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.flowCode").value(FlowCode.BAD_REQUEST))
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("formato UUID")));
    }

    @Test
    @DisplayName("PATCH /api/processes/{id}/status should update the execution")
    void updateExecutionStatusReturnsUpdated() throws Exception {
        when(processService.updateExecutionStatus(eq(EXECUTION_ID), any()))
                .thenReturn(response(ProcessExecutionStatus.FAILED));

        mockMvc.perform(patch("/api/processes/{id}/status", EXECUTION_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                ProcessExecutionStatusRequest.of(ProcessExecutionStatus.FAILED))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"));
    }

    @Test
    @DisplayName("PATCH /api/processes/{id}/status should reject unknown enum values")
    void updateExecutionStatusRejectsUnknownEnum() throws Exception {
        mockMvc.perform(patch("/api/processes/{id}/status", EXECUTION_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"NOT_A_STATUS\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.flowCode").value(FlowCode.BAD_REQUEST))
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("El cuerpo de la solicitud no es válido")));
    }

    @Test
    @DisplayName("POST /api/processes/{id}/retry should return 409 when the state is not retryable")
    void retryProcessReturnsConflict() throws Exception {
        when(flowInvocationService.retryProcess(EXECUTION_ID, null))
                .thenThrow(new InvalidProcessStateException(EXECUTION_ID, ProcessExecutionStatus.IN_PROGRESS));

        mockMvc.perform(post("/api/processes/{id}/retry", EXECUTION_ID))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.flowCode").value(FlowCode.INVALID_PROCESS_STATE))
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("no se puede reintentar")));
    }

    @Test
    @DisplayName("POST /api/processes/{id}/retry should pass the user input")
    void retryProcessPassesUserInput() throws Exception {
        when(flowInvocationService.retryProcess(EXECUTION_ID, "RFC-123"))
                .thenReturn(response(ProcessExecutionStatus.IN_PROGRESS));

        mockMvc.perform(post("/api/processes/{id}/retry", EXECUTION_ID)
                        .param("userInput", "RFC-123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));

        verify(flowInvocationService).retryProcess(EXECUTION_ID, "RFC-123");
    }

    @Test
    @DisplayName("POST /api/processes/{id}/steps/{order}/retry should return 502 on orchestrator failure")
    void retryStepReturnsBadGateway() throws Exception {
        org.mockito.Mockito.doThrow(new OrchestratorUnavailableException("retry step", new RuntimeException("down")))
                .when(flowInvocationService).retryStep(EXECUTION_ID, 1);

        mockMvc.perform(post("/api/processes/{id}/steps/{order}/retry", EXECUTION_ID, 1))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.flowCode").value(FlowCode.ORCHESTRATOR_UNAVAILABLE));
    }

    @Test
    @DisplayName("POST /api/processes/{id}/steps/{order}/retry should return the refreshed execution")
    void retryStepReturnsExecution() throws Exception {
        when(processService.getExecution(EXECUTION_ID)).thenReturn(response(ProcessExecutionStatus.IN_PROGRESS));

        mockMvc.perform(post("/api/processes/{id}/steps/{order}/retry", EXECUTION_ID, 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));

        verify(flowInvocationService).retryStep(EXECUTION_ID, 1);
    }

    @Test
    @DisplayName("PUT /api/processes/{id}/steps/{order} should update a step")
    void updateStepReturnsStep() throws Exception {
        ProcessExecutionStepResponse step = new ProcessExecutionStepResponse(
                UUID.randomUUID().toString(), 1, "Consultar vehículo", StepExecutionStatus.COMPLETED,
                0, null, OffsetDateTime.now(), OffsetDateTime.now());
        when(processService.updateStepExecution(eq(EXECUTION_ID), eq(1), any())).thenReturn(step);

        mockMvc.perform(put("/api/processes/{id}/steps/{order}", EXECUTION_ID, 1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"COMPLETED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Consultar vehículo"))
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    @DisplayName("GET /api/processes/{id}/steps should return the step list")
    void getStepsReturnsList() throws Exception {
        when(processService.getExecutionSteps(EXECUTION_ID)).thenReturn(List.of(
                new ProcessExecutionStepResponse(UUID.randomUUID().toString(), 1, "Paso 1",
                        StepExecutionStatus.PENDING, 0, null, null, null)));

        mockMvc.perform(get("/api/processes/{id}/steps", EXECUTION_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Paso 1"));
    }

    @Test
    @DisplayName("GET /api/processes should return the first page of executions")
    void listExecutionsReturnsFirstPage() throws Exception {
        when(processService.listExecutions(0, 10)).thenReturn(new ProcessExecutionPage(
                List.of(response(ProcessExecutionStatus.PENDING)), 0, 10, 1, 1));

        mockMvc.perform(get("/api/processes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(EXECUTION_ID.toString()))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1));
    }

    @Test
    @DisplayName("GET /api/processes should forward pagination parameters")
    void listExecutionsForwardsPagination() throws Exception {
        when(processService.listExecutions(2, 5))
                .thenReturn(new ProcessExecutionPage(List.of(), 2, 5, 12, 3));

        mockMvc.perform(get("/api/processes").param("page", "2").param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(2))
                .andExpect(jsonPath("$.totalPages").value(3));

        verify(processService).listExecutions(2, 5);
    }

    @Test
    @DisplayName("GET /api/processes should reject out-of-range pagination")
    void listExecutionsRejectsInvalidPagination() throws Exception {
        mockMvc.perform(get("/api/processes").param("size", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.flowCode").value(FlowCode.VALIDATION));

        mockMvc.perform(get("/api/processes").param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.flowCode").value(FlowCode.VALIDATION));

        verify(processService, never()).listExecutions(anyInt(), anyInt());
    }

    @Test
    @DisplayName("GET /api/process-templates/by-name should require the name parameter")
    void getTemplateByNameRequiresName() throws Exception {
        mockMvc.perform(get("/api/process-templates/by-name"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.flowCode").value(FlowCode.BAD_REQUEST))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("name")));
    }

    @Test
    @DisplayName("GET /api/process-templates/default should return the default template")
    void getDefaultTemplateReturnsId() throws Exception {
        when(processService.getDefaultProcessTemplateId()).thenReturn(TEMPLATE_ID.toString());

        mockMvc.perform(get("/api/process-templates/default"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(TEMPLATE_ID.toString()));
    }

    @Test
    @DisplayName("GET /api/processes/health should report the service status")
    void healthReturnsOk() throws Exception {
        mockMvc.perform(get("/api/processes/health"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string("Control Center OK"));
    }

    private ProcessExecutionRequest validRequest() {
        return new ProcessExecutionRequest(TEMPLATE_ID.toString(), "customer-004", "vehicle-1", "agent-1");
    }

    private ProcessExecutionResponse response(ProcessExecutionStatus status) {
        return new ProcessExecutionResponse(
                EXECUTION_ID.toString(),
                TEMPLATE_ID.toString(),
                "customer-004",
                status,
                "Consultar vehículo",
                null,
                null,
                null,
                null,
                null,
                "trace-1",
                List.of("Consultar vehículo"));
    }
}
