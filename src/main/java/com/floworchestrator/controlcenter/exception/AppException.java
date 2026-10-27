package com.floworchestrator.controlcenter.exception;

import org.springframework.http.HttpStatus;

/**
 * Base de los errores de negocio. Separa el mensaje técnico (logs) del mensaje
 * accionable que se expone al cliente HTTP.
 */
public abstract class AppException extends RuntimeException {

    private final HttpStatus status;
    private final String flowCode;
    private final String userMessage;

    protected AppException(HttpStatus status, String flowCode, String userMessage, String technicalMessage) {
        this(status, flowCode, userMessage, technicalMessage, null);
    }

    protected AppException(HttpStatus status, String flowCode, String userMessage,
                           String technicalMessage, Throwable cause) {
        super(technicalMessage, cause);
        this.status = status;
        this.flowCode = flowCode;
        this.userMessage = userMessage;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getFlowCode() {
        return flowCode;
    }

    public String getUserMessage() {
        return userMessage;
    }
}
