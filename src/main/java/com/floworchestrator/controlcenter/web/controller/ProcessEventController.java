package com.floworchestrator.controlcenter.web.controller;

import com.floworchestrator.controlcenter.model.FlowExecutionEvent;
import com.floworchestrator.controlcenter.service.FlowEventSseConsumer;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.UUID;

/**
 * Endpoint SSE que expone los eventos de ejecución recibidos del Orchestrator.
 * <p>
 * El endpoint {@code /events} retransmite todos los eventos en tiempo real.
 * El endpoint {@code /{id}/events} filtra por una ejecución específica,
 * permitiendo a un frontend hacer seguimiento individual de un proceso.
 */
@RestController
@RequestMapping("/api/processes")
@RequiredArgsConstructor
public class ProcessEventController {

    private final FlowEventSseConsumer flowEventSseConsumer;

    @GetMapping(value = "/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<FlowExecutionEvent>> streamEvents() {
        return flowEventSseConsumer.stream().map(this::toServerSentEvent);
    }

    @GetMapping(value = "/{id}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<FlowExecutionEvent>> streamEventsForExecution(@PathVariable UUID id) {
        String executionId = id.toString();
        flowEventSseConsumer.trackExecution(executionId);
        return flowEventSseConsumer.stream()
                .filter(event -> executionId.equals(event.executionId()))
                .map(this::toServerSentEvent);
    }

    private ServerSentEvent<FlowExecutionEvent> toServerSentEvent(FlowExecutionEvent event) {
        long epochMilli = event.timestamp() != null
                ? event.timestamp().toInstant().toEpochMilli()
                : System.currentTimeMillis();
        return ServerSentEvent.<FlowExecutionEvent>builder()
                .id(event.executionId() + "-" + epochMilli)
                .data(event)
                .build();
    }
}
