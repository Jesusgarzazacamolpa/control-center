package com.floworchestrator.controlcenter.web.controller;

import com.floworchestrator.controlcenter.model.FlowExecutionEvent;
import com.floworchestrator.controlcenter.service.FlowEventSseConsumer;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

/**
 * Endpoint SSE que expone los eventos de ejecución recibidos del Orchestrator.
 * <p>
 * Cuando un cliente realiza una petición a este endpoint, el Control Center se
 * conecta al stream SSE del Orchestrator, realiza el bitácora de los pasos y
 * ejecuciones en base de datos, y retransmite los eventos al cliente en tiempo real.
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
}
