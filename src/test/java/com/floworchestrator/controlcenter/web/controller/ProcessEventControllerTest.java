package com.floworchestrator.controlcenter.web.controller;

import com.floworchestrator.controlcenter.model.FlowExecutionEvent;
import com.floworchestrator.controlcenter.service.FlowEventSseConsumer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import reactor.core.publisher.Flux;

import java.time.OffsetDateTime;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProcessEventController.class)
class ProcessEventControllerTest {

    private static final String EXECUTION_ID = "11111111-1111-1111-1111-111111111111";
    private static final String OTHER_EXECUTION_ID = "22222222-2222-2222-2222-222222222222";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private FlowEventSseConsumer flowEventSseConsumer;

    private static FlowExecutionEvent event(String executionId, String eventType) {
        return new FlowExecutionEvent(executionId, "vehicle-purchase", eventType, 1, "Consultar vehículo",
                "get_vehicle_information", "mcp-domain", "RUNNING", null, null, null, null, null,
                "trace-1", OffsetDateTime.parse("2026-01-15T10:00:00Z"));
    }

    @Test
    @DisplayName("GET /api/processes/events should stream every event with an id")
    void streamsAllEvents() throws Exception {
        when(flowEventSseConsumer.stream()).thenReturn(
                Flux.just(event("exec-1", "FLOW_STARTED"), event("exec-2", "FLOW_COMPLETED")));

        MvcResult result = mockMvc.perform(get("/api/processes/events")
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(request().asyncStarted())
                .andReturn();

        result.getAsyncResult(5_000);

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("data:")))
                .andExpect(content().string(containsString("\"executionId\":\"exec-1\"")))
                .andExpect(content().string(containsString("\"eventType\":\"FLOW_STARTED\"")))
                .andExpect(content().string(containsString("\"executionId\":\"exec-2\"")))
                .andExpect(content().string(containsString("\"eventType\":\"FLOW_COMPLETED\"")));
    }

    @Test
    @DisplayName("GET /api/processes/{id}/events should filter events and track the execution")
    void streamsEventsForExecution() throws Exception {
        when(flowEventSseConsumer.stream()).thenReturn(
                Flux.just(event(EXECUTION_ID, "FLOW_STARTED"), event(OTHER_EXECUTION_ID, "FLOW_COMPLETED")));

        MvcResult result = mockMvc.perform(get("/api/processes/{id}/events", EXECUTION_ID)
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(request().asyncStarted())
                .andReturn();

        result.getAsyncResult(5_000);

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"executionId\":\"" + EXECUTION_ID + "\"")))
                .andExpect(content().string(not(containsString("\"executionId\":\"" + OTHER_EXECUTION_ID + "\""))));

        verify(flowEventSseConsumer).trackExecution(EXECUTION_ID);
    }

    @Test
    @DisplayName("GET /api/processes/{id}/events should reject a non UUID identifier")
    void rejectsInvalidExecutionId() throws Exception {
        mockMvc.perform(get("/api/processes/not-a-uuid/events"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.flowCode").value("ERR_BAD_REQUEST_01"));
    }
}
