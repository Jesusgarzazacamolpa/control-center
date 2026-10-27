package com.floworchestrator.controlcenter.exception;

import com.floworchestrator.controlcenter.error.FlowCode;
import org.springframework.http.HttpStatus;

public class ProcessTemplateNotFoundException extends AppException {

    public ProcessTemplateNotFoundException(String templateName) {
        super(HttpStatus.NOT_FOUND,
                FlowCode.TEMPLATE_NOT_FOUND,
                "La plantilla de proceso solicitada no está disponible. Intenta con otra plantilla o vuelve a intentarlo más tarde.",
                "No se encontró la plantilla de proceso activa: " + templateName);
    }
}
