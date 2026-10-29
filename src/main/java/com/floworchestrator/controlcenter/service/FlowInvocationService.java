package com.floworchestrator.controlcenter.service;

import com.floworchestrator.controlcenter.client.FlowStartCommand;
import com.floworchestrator.controlcenter.client.OrchestratorClient;
import com.floworchestrator.controlcenter.entity.ProcessTemplateEntity;
import com.floworchestrator.controlcenter.enums.ProcessExecutionStatus;
import com.floworchestrator.controlcenter.exception.InvalidProcessStateException;
import com.floworchestrator.controlcenter.model.ProcessExecutionRequest;
import com.floworchestrator.controlcenter.model.ProcessExecutionResponse;
import com.floworchestrator.controlcenter.model.ProcessExecutionStatusRequest;
import com.floworchestrator.controlcenter.repository.ProcessTemplateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Caso de uso que coordina la creación de la ejecución en el Control Center con
 * el arranque, reanudación y reintento del flujo en el Orchestrator.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FlowInvocationService {

    private static final String DEFAULT_FLOW_NAME = "Compra de vehiculo";

    private final ProcessService processService;
    private final ProcessTemplateRepository processTemplateRepository;
    private final OrchestratorClient orchestratorClient;
    private final FlowEventSseConsumer flowEventSseConsumer;

    /**
     * Crea la ejecución en el Control Center y dispara el flujo en el Orchestrator.
     * El estatus de cada paso se reporta mediante eventos SSE.
     */
    public ProcessExecutionResponse startProcess(ProcessExecutionRequest request) {
        flowEventSseConsumer.ensureStarted();

        ProcessExecutionResponse execution = processService.createExecution(request);
        flowEventSseConsumer.trackExecution(execution.id());

        UUID executionId = UUID.fromString(execution.id());
        startFlow(executionId, resolveTemplateName(request.processTemplateId()), request);
        return execution;
    }

    /**
     * Reintenta una ejecución pausada o fallida. Si el Orchestrator no tiene
     * estado previo, reinicia el flujo desde el principio.
     */
    public ProcessExecutionResponse retryProcess(UUID executionId, String userInput) {
        ProcessExecutionResponse current = processService.getExecution(executionId);
        if (!current.status().isRetryable()) {
            throw new InvalidProcessStateException(executionId, current.status());
        }

        resumeOrRestartFlow(executionId, userInput);
        return processService.getExecution(executionId);
    }

    /**
     * Reanuda la ejecución y, si el Orchestrator ya no tiene el estado pausado
     * (por ejemplo, tras un reinicio), la reinicia desde el principio para que
     * el proceso no quede detenido. En ambos casos se vuelve a registrar la
     * ejecución en el consumidor SSE.
     *
     * @return {@code true} si se reanudó; {@code false} si se reinició el flujo.
     */
    public boolean resumeOrRestartFlow(UUID executionId, String userInput) {
        flowEventSseConsumer.reRegisterExecution(executionId.toString());
        if (resumeFlow(executionId, userInput)) {
            return true;
        }
        log.warn("[RESUME] El orquestador perdió el estado pausado; se reinicia el flujo: executionId={}",
                executionId);
        restartFlow(executionId);
        return false;
    }

    /**
     * Notifica al Orchestrator que reanude un flujo pausado.
     *
     * @return {@code false} cuando no existe estado previo y aplica un reinicio.
     */
    public boolean resumeFlow(UUID executionId, String userInput) {
        log.info("[RESUME] Notificando al orquestador para reanudar la ejecución: executionId={}", executionId);
        boolean resumed = orchestratorClient.resumeFlow(executionId.toString(), userInput);
        if (resumed) {
            log.info("[RESUME] El orquestador reanudó la ejecución: executionId={}", executionId);
        } else {
            log.warn("[RESUME] No se encontró estado pausado; se requiere reinicio de respaldo: executionId={}", executionId);
        }
        return resumed;
    }

    /**
     * Notifica al Orchestrator que cancele la ejecución y descarte su estado.
     * No existe estado que descartar cuando el flujo ya había terminado o el
     * Orchestrator se reinició; ese caso no debe romper el rechazo de la
     * intervención, que ya quedó registrado en el Control Center.
     *
     * @return {@code false} cuando el Orchestrator no tenía estado cancelable.
     */
    public boolean cancelFlow(UUID executionId) {
        log.info("[CANCEL] Notificando al orquestador para cancelar la ejecución: executionId={}", executionId);
        boolean cancelled = orchestratorClient.cancelFlow(executionId.toString());
        if (cancelled) {
            log.info("[CANCEL] El orquestador canceló la ejecución: executionId={}", executionId);
        } else {
            log.warn("[CANCEL] No había estado cancelable en el orquestador: executionId={}", executionId);
        }
        return cancelled;
    }

    /**
     * Reinicia el flujo desde el principio cuando el Orchestrator perdió el estado.
     */
    public void restartFlow(UUID executionId) {
        log.info("[RESTART] Iniciando reinicio de respaldo para la ejecución: executionId={}", executionId);
        ProcessExecutionRequest request = processService.getExecutionRequest(executionId);
        processService.updateExecutionStatus(executionId,
                ProcessExecutionStatusRequest.of(ProcessExecutionStatus.IN_PROGRESS));
        startFlow(executionId, resolveTemplateName(request.processTemplateId()), request);
    }

    /**
     * Marca el paso para reintento y reanuda el flujo: marcar el paso sin
     * reanudar dejaba al usuario con la sensación de que no ocurría nada.
     */
    public void retryStep(UUID executionId, int stepOrder) {
        log.info("[RETRY-STEP] Notificando al orquestador para reintentar el paso: executionId={}, stepOrder={}",
                executionId, stepOrder);
        orchestratorClient.retryStep(executionId.toString(), stepOrder);
        flowEventSseConsumer.reRegisterExecution(executionId.toString());

        if (!resumeFlow(executionId, null)) {
            log.warn("[RETRY-STEP] El paso quedó marcado pero no hay estado para reanudar: "
                    + "executionId={}, stepOrder={}", executionId, stepOrder);
            return;
        }
        log.info("[RETRY-STEP] Paso marcado y flujo reanudado: executionId={}, stepOrder={}",
                executionId, stepOrder);
    }

    /**
     * Envía el comando de arranque de forma asíncrona: el resultado del flujo no
     * bloquea la petición y un fallo se persiste como ejecución FAILED.
     */
    private void startFlow(UUID executionId, String flowName, ProcessExecutionRequest request) {
        FlowStartCommand command = new FlowStartCommand(
                executionId.toString(),
                flowName,
                request.customerId(),
                request.vehicleId(),
                request.salesAgentId());

        log.info("[FLOW] Invocando al orquestador: executionId={}, flowId={}, customerId={}",
                executionId, flowName, request.customerId());
        orchestratorClient.startFlow(command).subscribe(
                ignored -> log.info("[FLOW] El orquestador terminó la ejecución: executionId={}", executionId),
                error -> handleStartFailure(executionId, error));
    }

    /**
     * Resuelve el nombre de la plantilla que espera el Orchestrator; si el id no
     * existe o no es UUID usa el flujo predeterminado.
     */
    private String resolveTemplateName(String processTemplateId) {
        try {
            return processTemplateRepository.findById(UUID.fromString(processTemplateId))
                    .map(ProcessTemplateEntity::getName)
                    .orElse(DEFAULT_FLOW_NAME);
        } catch (IllegalArgumentException ex) {
            log.warn("[FLOW] processTemplateId inválido; se usa el flujo predeterminado: {}", processTemplateId);
            return DEFAULT_FLOW_NAME;
        }
    }

    private void handleStartFailure(UUID executionId, Throwable error) {
        log.error("[FLOW] El orquestador no pudo iniciar la ejecución: executionId={}", executionId, error);
        ProcessExecutionStatusRequest request = new ProcessExecutionStatusRequest(
                ProcessExecutionStatus.FAILED,
                null,
                "Falló la invocación del orquestador: " + error.getMessage(),
                "No pudimos iniciar tu proceso",
                "Ocurrió un problema al comunicarnos con el servicio de orquestación. Vuelve a intentarlo en unos segundos.",
                "Reintentar inicio",
                "error");
        try {
            processService.updateExecutionStatus(executionId, request);
        } catch (Exception ex) {
            log.error("[FLOW] No se pudo persistir el fallo de inicio: executionId={}", executionId, ex);
        }
    }
}
