package com.floworchestrator.controlcenter.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Solicitud de creación de una ejecución de proceso.
 */
public record ProcessExecutionRequest(
        @NotBlank(message = "El identificador de la plantilla de proceso es obligatorio.")
        @Pattern(regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$",
                message = "El identificador de la plantilla no es válido. Debe tener formato UUID.")
        String processTemplateId,

        @NotBlank(message = "El identificador del cliente es obligatorio.")
        String customerId,

        String vehicleId,

        String salesAgentId
) {
}
