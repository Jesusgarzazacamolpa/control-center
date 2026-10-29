package com.floworchestrator.controlcenter.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Definición de un paso de la plantilla (tabla {@code step_template}).
 * El {@code stepOrder} identifica el paso dentro del proceso.
 */
@Entity
@Table(name = "step_template")
@Data
@NoArgsConstructor
public class StepTemplateEntity {
    @Id
    private UUID id;

    @Column(name = "process_template_id", nullable = false)
    private UUID processTemplateId;

    @Column(name = "step_order", nullable = false)
    private int stepOrder;

    @Column(nullable = false)
    private String name;
}