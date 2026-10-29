package com.floworchestrator.controlcenter.entity;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.floworchestrator.controlcenter.enums.InterventionStatus;
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

/**
 * Intervención humana sobre una ejecución pausada (tabla {@code human_intervention}).
 * Guarda los mensajes de error visibles al usuario y el estado de revisión.
 */
@Entity
@Table(name = "human_intervention")
@Data
@NoArgsConstructor
public class InterventionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "process_execution_id", nullable = false, columnDefinition = "uuid")
    private UUID processExecutionId;

    @Column(name = "step_name")
    private String stepName;

    @Column(name = "step_order")
    private int stepOrder;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false, columnDefinition = "intervention_status")
    private InterventionStatus status = InterventionStatus.PENDING_REVIEW;

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

    @Column(name = "assigned_to")
    private String assignedTo;

    @Column(name = "resolution_notes", columnDefinition = "text")
    private String resolutionNotes;

    @Column(name = "suggested_action", columnDefinition = "text")
    private String suggestedAction;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
