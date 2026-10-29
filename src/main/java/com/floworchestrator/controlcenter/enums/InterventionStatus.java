package com.floworchestrator.controlcenter.enums;

/**
 * Estatus de una intervención humana: pendiente de revisión, aprobada o rechazada.
 * Se persiste como el tipo PostgreSQL {@code intervention_status}.
 */
public enum InterventionStatus {
    PENDING_REVIEW,
    APPROVED,
    REJECTED
}
