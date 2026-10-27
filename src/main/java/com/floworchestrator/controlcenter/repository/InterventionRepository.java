package com.floworchestrator.controlcenter.repository;

import com.floworchestrator.controlcenter.entity.InterventionEntity;
import com.floworchestrator.controlcenter.enums.InterventionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface InterventionRepository extends JpaRepository<InterventionEntity, UUID> {
    List<InterventionEntity> findByStatusOrderByCreatedAtDesc(InterventionStatus status);

    List<InterventionEntity> findByProcessExecutionIdOrderByCreatedAtDesc(UUID processExecutionId);
}
