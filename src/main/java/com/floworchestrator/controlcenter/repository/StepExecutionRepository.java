package com.floworchestrator.controlcenter.repository;

import com.floworchestrator.controlcenter.entity.StepExecutionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StepExecutionRepository extends JpaRepository<StepExecutionEntity, UUID> {
    Optional<StepExecutionEntity> findByProcessExecutionIdAndStepTemplateId(UUID processExecutionId, UUID stepTemplateId);
    List<StepExecutionEntity> findAllByProcessExecutionIdOrderByStartedAtAsc(UUID processExecutionId);
}