package com.floworchestrator.controlcenter.repository;

import com.floworchestrator.controlcenter.entity.StepTemplateEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.Optional;
import java.util.List;
import java.util.UUID;

/**
 * Acceso a la definición de pasos de las plantillas.
 */
public interface StepTemplateRepository extends JpaRepository<StepTemplateEntity, UUID> {
    /**
     * Busca la definición de un paso por plantilla y número de orden.
     */
    Optional<StepTemplateEntity> findByProcessTemplateIdAndStepOrder(UUID processTemplateId, int stepOrder);

    /**
     * Lista los pasos de una plantilla en orden ascendente.
     */
    List<StepTemplateEntity> findAllByProcessTemplateIdOrderByStepOrderAsc(UUID processTemplateId);

    /**
     * Lista los pasos de varias plantillas en una sola consulta (evita el N+1 al paginar).
     */
    List<StepTemplateEntity> findAllByProcessTemplateIdInOrderByStepOrderAsc(Collection<UUID> processTemplateIds);
}