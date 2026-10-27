package com.floworchestrator.controlcenter.exception;

import com.floworchestrator.controlcenter.error.FlowCode;
import org.springframework.http.HttpStatus;

import java.util.UUID;

public class StepNotFoundException extends AppException {

    public StepNotFoundException(UUID processTemplateId, int stepOrder) {
        super(HttpStatus.NOT_FOUND,
                FlowCode.STEP_NOT_FOUND,
                "El paso solicitado no existe dentro de este proceso. Actualiza la pantalla e intenta nuevamente.",
                "No se encontró la plantilla del paso: processTemplateId=" + processTemplateId + ", stepOrder=" + stepOrder);
    }
}
