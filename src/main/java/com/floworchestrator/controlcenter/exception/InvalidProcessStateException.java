package com.floworchestrator.controlcenter.exception;

import com.floworchestrator.controlcenter.enums.ProcessExecutionStatus;
import com.floworchestrator.controlcenter.error.FlowCode;
import org.springframework.http.HttpStatus;

import java.util.UUID;

public class InvalidProcessStateException extends AppException {

    public InvalidProcessStateException(UUID executionId, ProcessExecutionStatus currentStatus) {
        super(HttpStatus.CONFLICT,
                FlowCode.INVALID_PROCESS_STATE,
                "El proceso no se puede reintentar en su estado actual. Actualiza la pantalla y vuelve a intentarlo.",
                "La ejecución del proceso " + executionId + " no se puede reintentar desde el estatus " + currentStatus);
    }
}
