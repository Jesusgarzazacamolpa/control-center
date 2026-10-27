DROP TABLE IF EXISTS process_execution;
DROP TYPE IF EXISTS execution_status;

CREATE TYPE execution_status AS ENUM (
    'PENDING',
    'IN_PROGRESS',
    'PAUSED_FOR_HUMAN',
    'COMPLETED',
    'FAILED',
    'CANCELLED'
);

CREATE TABLE process_execution (
    id UUID PRIMARY KEY,
    process_template_id UUID NOT NULL,
    status execution_status DEFAULT 'PENDING',
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
    started_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,
    last_updated_at TIMESTAMP WITH TIME ZONE
);
