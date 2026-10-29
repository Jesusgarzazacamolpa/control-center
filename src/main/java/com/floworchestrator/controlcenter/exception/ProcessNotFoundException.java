package com.floworchestrator.controlcenter.exception;

import com.floworchestrator.controlcenter.error.FlowCode;
import org.springframework.http.HttpStatus;

import java.util.UUID;

/**
 * Se lanza cuando la ejecución de proceso solicitada no existe; responde 404.
 */
public class ProcessNotFoundException extends AppException {

    public ProcessNotFoundException(UUID executionId) {
        super(HttpStatus.NOT_FOUND,
                FlowCode.PROCESS_NOT_FOUND,
                "No encontramos el proceso solicitado. Actualiza la pantalla e intenta nuevamente.",
                "No se encontró la ejecución del proceso: " + executionId);
    }
}
