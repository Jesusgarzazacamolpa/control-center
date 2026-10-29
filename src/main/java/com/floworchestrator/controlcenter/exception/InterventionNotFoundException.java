package com.floworchestrator.controlcenter.exception;

import com.floworchestrator.controlcenter.error.FlowCode;
import org.springframework.http.HttpStatus;

import java.util.UUID;

/**
 * Se lanza cuando la intervención solicitada no existe; responde 404.
 */
public class InterventionNotFoundException extends AppException {

    public InterventionNotFoundException(UUID interventionId) {
        super(HttpStatus.NOT_FOUND,
                FlowCode.INTERVENTION_NOT_FOUND,
                "No encontramos la intervención solicitada. Actualiza la lista e intenta nuevamente.",
                "No se encontró la intervención: " + interventionId);
    }
}
