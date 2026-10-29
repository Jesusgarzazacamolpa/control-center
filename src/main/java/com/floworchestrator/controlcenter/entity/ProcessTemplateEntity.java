package com.floworchestrator.controlcenter.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Plantilla de proceso (tabla {@code process_template}) que el Orchestrator usa
 * como definición del flujo; solo las activas pueden iniciar ejecuciones.
 */
@Entity
@Table(name = "process_template")
@Data
@NoArgsConstructor
public class ProcessTemplateEntity {
    @Id
    private UUID id;

    private String name;

    @Column(name = "is_active")
    private boolean active;
}