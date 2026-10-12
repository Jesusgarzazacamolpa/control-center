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

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    @Column(name = "started_at")
    private OffsetDateTime startedAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @Column(name = "last_updated_at")
    private OffsetDateTime lastUpdatedAt;

    public String getProcessTemplateId() {
        return processTemplateId != null ? processTemplateId.toString() : null;
    }

    public UUID getProcessTemplateIdUuid() {
        return processTemplateId;
    }

    public void setProcessTemplateId(String processTemplateId) {
        this.processTemplateId = UUID.fromString(processTemplateId);
    }

    public void setProcessTemplateId(UUID processTemplateId) {
        this.processTemplateId = processTemplateId;
    }

    public String getStatus() {
        return status.name();
    }

    public void setStatus(String status) {
        this.status = ProcessExecutionStatus.valueOf(status);
    }

    public void setStatus(ProcessExecutionStatus status) {
        this.status = status;
    }
}
