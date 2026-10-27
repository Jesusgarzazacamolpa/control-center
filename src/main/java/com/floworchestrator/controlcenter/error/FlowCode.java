package com.floworchestrator.controlcenter.error;

/**
 * Códigos de flujo expuestos al cliente junto con cada error. Permiten que la UI
 * y el equipo de soporte identifiquen el escenario exacto sin depender del texto
 * del mensaje.
 */
public final class FlowCode {

    public static final String PROCESS_NOT_FOUND = "ERR_PROCESS_NOT_FOUND_01";
    public static final String TEMPLATE_NOT_FOUND = "ERR_TEMPLATE_NOT_FOUND_01";
    public static final String STEP_NOT_FOUND = "ERR_STEP_NOT_FOUND_01";
    public static final String INTERVENTION_NOT_FOUND = "ERR_INTERVENTION_NOT_FOUND_01";
    public static final String INVALID_PROCESS_STATE = "ERR_PROCESS_STATE_01";
    public static final String INVALID_INTERVENTION_STATUS = "ERR_INTERVENTION_STATUS_01";
    public static final String VALIDATION = "ERR_VALIDATION_01";
    public static final String BAD_REQUEST = "ERR_BAD_REQUEST_01";
    public static final String RESOURCE_NOT_FOUND = "ERR_RESOURCE_NOT_FOUND_01";
    public static final String METHOD_NOT_ALLOWED = "ERR_METHOD_NOT_ALLOWED_01";
    public static final String UNSUPPORTED_MEDIA_TYPE = "ERR_UNSUPPORTED_MEDIA_TYPE_01";
    public static final String ORCHESTRATOR_UNAVAILABLE = "ERR_ORCHESTRATOR_UNAVAILABLE_01";
    public static final String INTERNAL = "ERR_INTERNAL_01";

    private FlowCode() {
    }
}
