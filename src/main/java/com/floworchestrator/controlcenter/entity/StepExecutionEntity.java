package com.floworchestrator.controlcenter.entity;

import com.floworchestrator.controlcenter.enums.StepExecutionStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Estado de un paso dentro de una ejecución (tabla {@code step_execution}).
 * Registra estatus, reintentos y los mensajes de error del paso.
 */
@Entity
@Table(name = "step_execution")
@Data
@NoArgsConstructor
public class StepExecutionEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "process_execution_id", nullable = false)
    private UUID processExecutionId;

    @Column(name = "step_template_id", nullable = false)
    private UUID stepTemplateId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false, columnDefinition = "step_status")
    private StepExecutionStatus status = StepExecutionStatus.PENDING;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    @Column(name = "started_at")
    private OffsetDateTime startedAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;
}