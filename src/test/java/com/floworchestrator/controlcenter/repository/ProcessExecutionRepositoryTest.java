package com.floworchestrator.controlcenter.repository;

import com.floworchestrator.controlcenter.entity.ProcessExecutionEntity;
import com.floworchestrator.controlcenter.enums.ProcessExecutionStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.flyway.enabled=false"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
@Sql(scripts = "/db/process-execution-it.sql")
class ProcessExecutionRepositoryTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private ProcessExecutionRepository repository;

    @Test
    @DisplayName("should persist jsonb context and named enum against real PostgreSQL")
    void persistsJsonbContextAndNamedEnum() {
        ProcessExecutionEntity entity = new ProcessExecutionEntity();
        entity.setId(UUID.randomUUID());
        entity.setProcessTemplateId(UUID.randomUUID());
        entity.setStatus(ProcessExecutionStatus.PAUSED_FOR_HUMAN);
        entity.setGlobalContext("{\"customerId\":\"customer-9\"}");
        entity.setCurrentStep("Validar RFC");
        entity.setStartedAt(OffsetDateTime.now(ZoneOffset.UTC));
        entity.setLastUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));

        ProcessExecutionEntity saved = repository.saveAndFlush(entity);

        Optional<ProcessExecutionEntity> found = repository.findById(saved.getId());
        assertTrue(found.isPresent());
        assertEquals(ProcessExecutionStatus.PAUSED_FOR_HUMAN, found.get().getStatus());
        assertTrue(found.get().getGlobalContext().contains("customer-9"));
        assertEquals("Validar RFC", found.get().getCurrentStep());
        assertEquals(1, repository.count());
    }
}
