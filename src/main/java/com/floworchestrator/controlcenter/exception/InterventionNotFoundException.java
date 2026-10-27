package com.floworchestrator.controlcenter.exception;

import com.floworchestrator.controlcenter.error.FlowCode;
import org.springframework.http.HttpStatus;

import java.util.UUID;

public class InterventionNotFoundException extends AppException {

    public InterventionNotFoundException(UUID interventionId) {
        super(HttpStatus.NOT_FOUND,
                FlowCode.INTERVENTION_NOT_FOUND,
                "No encontramos la intervención solicitada. Actualiza la lista e intenta nuevamente.",
                "Intervention not found: " + interventionId);
    }
}
