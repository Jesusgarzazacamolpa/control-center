package com.floworchestrator.controlcenter.repository;

import com.floworchestrator.controlcenter.entity.StepTemplateEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.List;
import java.util.UUID;

public interface StepTemplateRepository extends JpaRepository<StepTemplateEntity, UUID> {
    Optional<StepTemplateEntity> findByProcessTemplateIdAndStepOrder(UUID processTemplateId, int stepOrder);
    List<StepTemplateEntity> findAllByProcessTemplateIdOrderByStepOrderAsc(UUID processTemplateId);
}