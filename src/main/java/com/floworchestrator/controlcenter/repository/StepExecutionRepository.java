package com.floworchestrator.controlcenter.repository;

import com.floworchestrator.controlcenter.entity.StepExecutionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Acceso a los pasos ejecutados de cada proceso.
 */
public interface StepExecutionRepository extends JpaRepository<StepExecutionEntity, UUID> {
    /**
     * Busca el registro de un paso concreto dentro de una ejecución.
     */
    Optional<StepExecutionEntity> findByProcessExecutionIdAndStepTemplateId(UUID processExecutionId, UUID stepTemplateId);

    /**
     * Lista los pasos de una ejecución en orden de inicio ascendente.
     */
    List<StepExecutionEntity> findAllByProcessExecutionIdOrderByStartedAtAsc(UUID processExecutionId);
}