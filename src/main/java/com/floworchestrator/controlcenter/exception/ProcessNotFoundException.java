package com.floworchestrator.controlcenter.exception;

import com.floworchestrator.controlcenter.error.FlowCode;
import org.springframework.http.HttpStatus;

import java.util.UUID;

public class ProcessNotFoundException extends AppException {

    public ProcessNotFoundException(UUID executionId) {
        super(HttpStatus.NOT_FOUND,
                FlowCode.PROCESS_NOT_FOUND,
                "No encontramos el proceso solicitado. Actualiza la pantalla e intenta nuevamente.",
                "Process execution not found: " + executionId);
    }
}
