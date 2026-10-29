package com.floworchestrator.controlcenter.model;

import java.util.List;

/**
 * Página de ejecuciones ordenada por fecha de inicio descendente.
 */
public record ProcessExecutionPage(
        List<ProcessExecutionResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}
