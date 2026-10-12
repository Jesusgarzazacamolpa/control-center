package com.floworchestrator.controlcenter.repository;

import com.floworchestrator.controlcenter.entity.ProcessExecutionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ProcessExecutionRepository extends JpaRepository<ProcessExecutionEntity, UUID> {
}
