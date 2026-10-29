package com.floworchestrator.controlcenter.repository;

import com.floworchestrator.controlcenter.entity.InterventionEntity;
import com.floworchestrator.controlcenter.enums.InterventionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Acceso a las intervenciones humanas. Todas las consultas devuelven las más
 * recientes primero.
 */
@Repository
public interface InterventionRepository extends JpaRepository<InterventionEntity, UUID> {
    /**
     * Lista las intervenciones de un estatus concreto.
     */
    List<InterventionEntity> findByStatusOrderByCreatedAtDesc(InterventionStatus status);

    /**
     * Lista todas las intervenciones de una ejecución.
     */
    List<InterventionEntity> findByProcessExecutionIdOrderByCreatedAtDesc(UUID processExecutionId);

    /**
     * Lista las intervenciones de una ejecución filtradas por estatus.
     */
    List<InterventionEntity> findByProcessExecutionIdAndStatusOrderByCreatedAtDesc(
            UUID processExecutionId, InterventionStatus status);
}
