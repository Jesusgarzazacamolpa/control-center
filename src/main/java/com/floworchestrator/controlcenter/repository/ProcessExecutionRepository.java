package com.floworchestrator.controlcenter.repository;

import com.floworchestrator.controlcenter.entity.ProcessExecutionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Acceso a las ejecuciones de proceso; expone solo las operaciones CRUD y de
 * paginación heredadas de {@link JpaRepository}.
 */
@Repository
public interface ProcessExecutionRepository extends JpaRepository<ProcessExecutionEntity, UUID> {
}
