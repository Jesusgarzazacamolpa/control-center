package com.floworchestrator.controlcenter.repository;

import com.floworchestrator.controlcenter.entity.ProcessTemplateEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Acceso al catálogo de plantillas de proceso.
 */
public interface ProcessTemplateRepository extends JpaRepository<ProcessTemplateEntity, UUID> {
    /**
     * Busca la primera plantilla activa con ese nombre; vacío si no existe.
     */
    Optional<ProcessTemplateEntity> findFirstByNameAndActiveTrue(String name);
}