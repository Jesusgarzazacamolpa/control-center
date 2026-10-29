package com.floworchestrator.controlcenter.exception;

import com.floworchestrator.controlcenter.error.FlowCode;
import org.springframework.http.HttpStatus;

/**
 * Se lanza cuando falla la comunicación HTTP con el Orchestrator; responde 502
 * para distinguir la dependencia caída de un error propio.
 */
public class OrchestratorUnavailableException extends AppException {

    public OrchestratorUnavailableException(String operation, Throwable cause) {
        super(HttpStatus.BAD_GATEWAY,
                FlowCode.ORCHESTRATOR_UNAVAILABLE,
                "No pudimos comunicarnos con el servicio de orquestación. Intenta nuevamente en unos segundos.",
                "Falló la llamada al orquestador durante: " + operation,
                cause);
    }
}
