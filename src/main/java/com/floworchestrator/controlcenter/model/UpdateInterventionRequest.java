package com.floworchestrator.controlcenter.model;

import jakarta.validation.constraints.Pattern;

/**
 * Actualización de una intervención: cambio de estatus y/o notas de resolución.
 */
public record UpdateInterventionRequest(
        @Pattern(regexp = "(?i)^(APPROVED|REJECTED)$",
                message = "El estatus no es válido. Usa APPROVED para aprobar o REJECTED para rechazar.")
        String status,

        String notes
) {
}
