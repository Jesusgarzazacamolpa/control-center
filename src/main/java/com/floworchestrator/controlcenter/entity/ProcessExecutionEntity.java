package com.floworchestrator.controlcenter.entity;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.floworchestrator.controlcenter.enums.ProcessExecutionStatus;
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
import org.hibernate.annotations.ColumnTransformer;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Ejecución de una plantilla de proceso (tabla {@code process_execution}).
 * Es la fuente de verdad del estatus, el paso actual y el contexto del cliente;
 * {@code globalContext} se persiste como jsonb.
 */
@Entity
@Table(name = "process_execution")
@Data
@NoArgsConstructor
public class ProcessExecutionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "process_template_id", nullable = false, columnDefinition = "uuid")
    private UUID processTemplateId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false, columnDefinition = "execution_status")
    private ProcessExecutionStatus status = ProcessExecutionStatus.PENDING;

    @Column(name = "global_context", columnDefinition = "jsonb")
    @ColumnTransformer(write = "?::jsonb")
    private String globalContext;

    @Column(name = "current_step")
    private String currentStep;

    @Column(name = "vehicle_id")
    private String vehicleId;

    @Column(name = "sales_agent_id")
    private String salesAgentId;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    @Column(name = "user_error_title", columnDefinition = "text")
    private String userErrorTitle;

    @Column(name = "user_error_message", columnDefinition = "text")
    private String userErrorMessage;

    @Column(name = "user_error_action_label", columnDefinition = "text")
    private String userErrorActionLabel;

    @Column(name = "user_error_status_type", columnDefinition = "text")
    private String userErrorStatusType;

    @Column(name = "trace_id")
    private String traceId;

    @Column(name = "started_at")
    private OffsetDateTime startedAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @Column(name = "last_updated_at")
    private OffsetDateTime lastUpdatedAt;
}
