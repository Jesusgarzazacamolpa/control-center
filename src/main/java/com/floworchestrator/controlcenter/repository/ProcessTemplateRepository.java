package com.floworchestrator.controlcenter.repository;

import com.floworchestrator.controlcenter.entity.ProcessTemplateEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ProcessTemplateRepository extends JpaRepository<ProcessTemplateEntity, UUID> {
    Optional<ProcessTemplateEntity> findFirstByNameAndActiveTrue(String name);
}