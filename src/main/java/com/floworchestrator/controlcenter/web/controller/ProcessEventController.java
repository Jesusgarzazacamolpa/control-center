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

/**
 * Endpoint SSE que expone los eventos de ejecución recibidos del Orchestrator.
 * <p>
 * El endpoint {@code /events} retransmite todos los eventos en tiempo real.
 * El endpoint {@code /{id}/events} filtra por una ejecución específica,
 * permitiendo a un cliente frontend hacer seguimiento individual de un proceso.
 */
@RestController
@RequestMapping("/api/processes")
@RequiredArgsConstructor
public class ProcessEventController {

    private final FlowEventSseConsumer flowEventSseConsumer;

    @GetMapping(value = "/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<FlowExecutionEvent>> streamEvents() {
        return flowEventSseConsumer.stream()
                .map(event -> ServerSentEvent.<FlowExecutionEvent>builder()
                        .id(event.executionId() + "-" + event.timestamp().toInstant().toEpochMilli())
                        .event(event.eventType())
                        .data(event)
                        .build());
    }

    @GetMapping(value = "/{id}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<FlowExecutionEvent>> streamEventsForExecution(@PathVariable String id) {
        flowEventSseConsumer.trackExecution(id);
        return flowEventSseConsumer.stream()
                .filter(event -> id.equals(event.executionId()))
                .map(event -> ServerSentEvent.<FlowExecutionEvent>builder()
                        .id(event.executionId() + "-" + event.timestamp().toInstant().toEpochMilli())
                        .event(event.eventType())
                        .data(event)
                        .build());
    }
}
