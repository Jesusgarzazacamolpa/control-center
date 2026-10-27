-- Alinea una base creada con database/init_schema.sql (y drift de ddl-auto)
-- con el modelo JPA actual. Todas las sentencias son idempotentes para que la
-- migración también pueda ejecutarse sobre un esquema creado por V1.

-- 1) Columnas agregadas a process_execution después del esquema inicial
ALTER TABLE process_execution ADD COLUMN IF NOT EXISTS vehicle_id VARCHAR(255);
ALTER TABLE process_execution ADD COLUMN IF NOT EXISTS sales_agent_id VARCHAR(255);
ALTER TABLE process_execution ADD COLUMN IF NOT EXISTS user_error_title TEXT;
ALTER TABLE process_execution ADD COLUMN IF NOT EXISTS user_error_message TEXT;
ALTER TABLE process_execution ADD COLUMN IF NOT EXISTS user_error_action_label TEXT;
ALTER TABLE process_execution ADD COLUMN IF NOT EXISTS user_error_status_type TEXT;
ALTER TABLE process_execution ADD COLUMN IF NOT EXISTS trace_id VARCHAR(255);
ALTER TABLE process_execution ADD COLUMN IF NOT EXISTS current_step VARCHAR(255);
ALTER TABLE process_execution ADD COLUMN IF NOT EXISTS error_message TEXT;

-- 2) process_execution.status debe ser el enum tipado que espera la entidad
UPDATE process_execution SET status = 'PENDING' WHERE status IS NULL;
ALTER TABLE process_execution
    ALTER COLUMN status TYPE execution_status USING status::execution_status;
ALTER TABLE process_execution ALTER COLUMN status SET DEFAULT 'PENDING';
ALTER TABLE process_execution ALTER COLUMN status SET NOT NULL;

-- 3) Columnas canónicas de human_intervention (el esquema anterior usaba
--    step_execution_id, requested_at, required_role, context_data, resolved_by)
ALTER TABLE human_intervention ADD COLUMN IF NOT EXISTS process_execution_id UUID;
ALTER TABLE human_intervention ADD COLUMN IF NOT EXISTS step_name VARCHAR(255);
ALTER TABLE human_intervention ADD COLUMN IF NOT EXISTS step_order INTEGER;
ALTER TABLE human_intervention ADD COLUMN IF NOT EXISTS error_message TEXT;
ALTER TABLE human_intervention ADD COLUMN IF NOT EXISTS user_error_title TEXT;
ALTER TABLE human_intervention ADD COLUMN IF NOT EXISTS user_error_message TEXT;
ALTER TABLE human_intervention ADD COLUMN IF NOT EXISTS user_error_action_label TEXT;
ALTER TABLE human_intervention ADD COLUMN IF NOT EXISTS user_error_status_type TEXT;
ALTER TABLE human_intervention ADD COLUMN IF NOT EXISTS trace_id VARCHAR(255);
ALTER TABLE human_intervention ADD COLUMN IF NOT EXISTS assigned_to VARCHAR(255);
ALTER TABLE human_intervention ADD COLUMN IF NOT EXISTS suggested_action TEXT;
ALTER TABLE human_intervention ADD COLUMN IF NOT EXISTS created_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE human_intervention ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP WITH TIME ZONE;

-- 4) Backfill desde el modelo legado cuando exista la columna step_execution_id
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'human_intervention'
          AND column_name = 'step_execution_id'
    ) THEN
        EXECUTE 'ALTER TABLE human_intervention ALTER COLUMN step_execution_id DROP NOT NULL';
        EXECUTE 'UPDATE human_intervention hi
                    SET process_execution_id = se.process_execution_id
                   FROM step_execution se
                  WHERE hi.process_execution_id IS NULL
                    AND hi.step_execution_id = se.id';
        EXECUTE 'UPDATE human_intervention hi
                    SET step_name = st.name,
                        step_order = st.step_order
                   FROM step_execution se
                   JOIN step_template st ON st.id = se.step_template_id
                  WHERE hi.step_name IS NULL
                    AND hi.step_execution_id = se.id';
    END IF;
END $$;

-- 5) created_at conserva requested_at cuando existe, luego se normaliza
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'human_intervention'
          AND column_name = 'requested_at'
    ) THEN
        EXECUTE 'UPDATE human_intervention SET created_at = requested_at WHERE created_at IS NULL';
    END IF;
END $$;

UPDATE human_intervention SET created_at = CURRENT_TIMESTAMP WHERE created_at IS NULL;
UPDATE human_intervention SET updated_at = created_at WHERE updated_at IS NULL;
UPDATE human_intervention SET step_order = 0 WHERE step_order IS NULL;
UPDATE human_intervention SET status = 'PENDING_REVIEW' WHERE status IS NULL;

ALTER TABLE human_intervention ALTER COLUMN step_order SET DEFAULT 0;
ALTER TABLE human_intervention ALTER COLUMN step_order SET NOT NULL;
ALTER TABLE human_intervention ALTER COLUMN status SET DEFAULT 'PENDING_REVIEW';
ALTER TABLE human_intervention ALTER COLUMN status SET NOT NULL;
ALTER TABLE human_intervention ALTER COLUMN created_at SET DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE human_intervention ALTER COLUMN created_at SET NOT NULL;
ALTER TABLE human_intervention ALTER COLUMN updated_at SET DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE human_intervention ALTER COLUMN updated_at SET NOT NULL;

-- 6) process_execution_id solo puede ser NOT NULL si el backfill cubrió todo
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM human_intervention WHERE process_execution_id IS NULL) THEN
        EXECUTE 'ALTER TABLE human_intervention ALTER COLUMN process_execution_id SET NOT NULL';
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'human_intervention'::regclass
          AND conname = 'human_intervention_process_execution_id_fkey'
    ) THEN
        EXECUTE 'ALTER TABLE human_intervention
                 ADD CONSTRAINT human_intervention_process_execution_id_fkey
                 FOREIGN KEY (process_execution_id) REFERENCES process_execution(id) ON DELETE CASCADE';
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_execution_status ON process_execution(status);
CREATE INDEX IF NOT EXISTS idx_step_status ON step_execution(status);
CREATE INDEX IF NOT EXISTS idx_step_execution_process ON step_execution(process_execution_id);
CREATE INDEX IF NOT EXISTS idx_intervention_status ON human_intervention(status);
CREATE INDEX IF NOT EXISTS idx_human_intervention_process ON human_intervention(process_execution_id);
