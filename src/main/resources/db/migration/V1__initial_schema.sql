-- Esquema canónico del control-center.
-- Idempotente para poder aplicarse sobre bases creadas con el esquema anterior
-- (docker-entrypoint-initdb.d / database/init_schema.sql).

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

DO $$
BEGIN
    CREATE TYPE execution_status AS ENUM (
        'PENDING',
        'IN_PROGRESS',
        'PAUSED_FOR_HUMAN',
        'COMPLETED',
        'FAILED',
        'CANCELLED'
    );
EXCEPTION
    WHEN duplicate_object THEN NULL;
END $$;

DO $$
BEGIN
    CREATE TYPE step_status AS ENUM (
        'PENDING',
        'RUNNING',
        'REQUIRES_INTERVENTION',
        'COMPLETED',
        'FAILED'
    );
EXCEPTION
    WHEN duplicate_object THEN NULL;
END $$;

DO $$
BEGIN
    CREATE TYPE intervention_status AS ENUM (
        'PENDING_REVIEW',
        'APPROVED',
        'REJECTED'
    );
EXCEPTION
    WHEN duplicate_object THEN NULL;
END $$;

CREATE TABLE IF NOT EXISTS process_template (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name VARCHAR(255) NOT NULL,
    description TEXT,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS step_template (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    process_template_id UUID NOT NULL REFERENCES process_template(id) ON DELETE CASCADE,
    step_order INTEGER NOT NULL,
    name VARCHAR(255) NOT NULL,
    mcp_target VARCHAR(100),
    expected_tool VARCHAR(255),
    requires_human BOOLEAN NOT NULL DEFAULT FALSE,
    config JSONB,
    UNIQUE (process_template_id, step_order)
);

CREATE TABLE IF NOT EXISTS process_execution (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    process_template_id UUID NOT NULL REFERENCES process_template(id),
    status execution_status NOT NULL DEFAULT 'PENDING',
    global_context JSONB,
    current_step VARCHAR(255),
    vehicle_id VARCHAR(255),
    sales_agent_id VARCHAR(255),
    error_message TEXT,
    user_error_title TEXT,
    user_error_message TEXT,
    user_error_action_label TEXT,
    user_error_status_type TEXT,
    trace_id VARCHAR(255),
    started_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP WITH TIME ZONE,
    last_updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS step_execution (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    process_execution_id UUID NOT NULL REFERENCES process_execution(id) ON DELETE CASCADE,
    step_template_id UUID NOT NULL REFERENCES step_template(id),
    status step_status NOT NULL DEFAULT 'PENDING',
    retry_count INTEGER NOT NULL DEFAULT 0,
    error_message TEXT,
    started_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE
);

CREATE TABLE IF NOT EXISTS audit_log (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    process_execution_id UUID NOT NULL REFERENCES process_execution(id),
    step_execution_id UUID REFERENCES step_execution(id),
    action_type VARCHAR(100) NOT NULL,
    payload JSONB NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS human_intervention (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    process_execution_id UUID NOT NULL REFERENCES process_execution(id) ON DELETE CASCADE,
    step_name VARCHAR(255),
    step_order INTEGER NOT NULL DEFAULT 0,
    status intervention_status NOT NULL DEFAULT 'PENDING_REVIEW',
    error_message TEXT,
    user_error_title TEXT,
    user_error_message TEXT,
    user_error_action_label TEXT,
    user_error_status_type TEXT,
    trace_id VARCHAR(255),
    assigned_to VARCHAR(255),
    resolution_notes TEXT,
    suggested_action TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_execution_status ON process_execution(status);
CREATE INDEX IF NOT EXISTS idx_step_status ON step_execution(status);
CREATE INDEX IF NOT EXISTS idx_step_execution_process ON step_execution(process_execution_id);
CREATE INDEX IF NOT EXISTS idx_intervention_status ON human_intervention(status);
CREATE INDEX IF NOT EXISTS idx_human_intervention_process ON human_intervention(process_execution_id);

INSERT INTO process_template (name, description)
SELECT 'Compra de vehiculo', 'Flujo principal de compra de auto con validaciones y pagos'
WHERE NOT EXISTS (SELECT 1 FROM process_template WHERE name = 'Compra de vehiculo');

INSERT INTO step_template (process_template_id, step_order, name, mcp_target, expected_tool, requires_human, config)
SELECT pt.id, s.step_order, s.name, s.mcp_target, s.expected_tool, s.requires_human, s.config::jsonb
FROM process_template pt
JOIN (VALUES
    (1, 'Consultar vehículo', 'mcp-domain', 'get_vehicle_information', FALSE, '{"agency_id":"default"}'),
    (2, 'Registrar compra', 'mcp-domain', 'register_vehicle_purchase', FALSE, '{}'),
    (3, 'Configurar pago', 'mcp-domain', 'set_payment_method', FALSE, '{}'),
    (4, 'Enviar correo', 'mcp-notifications', 'send_purchase_status_email', FALSE, '{}')
) AS s(step_order, name, mcp_target, expected_tool, requires_human, config) ON TRUE
WHERE pt.name = 'Compra de vehiculo'
  AND NOT EXISTS (
      SELECT 1 FROM step_template st
      WHERE st.process_template_id = pt.id AND st.step_order = s.step_order
  );
