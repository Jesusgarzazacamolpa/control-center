package com.floworchestrator.controlcenter.exception;

import com.floworchestrator.controlcenter.error.FlowCode;
import org.springframework.http.HttpStatus;

/**
 * Se lanza cuando el estatus de intervención recibido no es APPROVED ni REJECTED;
 * responde 400.
 */
public class InvalidInterventionStatusException extends AppException {

    public InvalidInterventionStatusException(String status) {
        super(HttpStatus.BAD_REQUEST,
                FlowCode.INVALID_INTERVENTION_STATUS,
                "El estatus enviado no es válido. Usa PENDING_REVIEW, APPROVED o REJECTED.",
                "Estatus de intervención inválido: " + status);
    }
}
