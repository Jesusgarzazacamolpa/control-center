-- Plantillas de los flujos alternativos alineadas con flow-definitions.yml del orquestador.
-- Los nombres deben coincidir exactamente: el Control Center los envía como flowId.

INSERT INTO process_template (name, description)
SELECT 'Compra con financiamiento', 'Compra de vehículo con autorización humana del financiamiento'
WHERE NOT EXISTS (SELECT 1 FROM process_template WHERE name = 'Compra con financiamiento');

INSERT INTO step_template (process_template_id, step_order, name, mcp_target, expected_tool, requires_human, config)
SELECT pt.id, s.step_order, s.name, s.mcp_target, s.expected_tool, s.requires_human, s.config::jsonb
FROM process_template pt
JOIN (VALUES
    (1, 'Consultar vehículo', 'mcp-domain', 'get_vehicle_information', FALSE, '{"agency_id":"default"}'),
    (2, 'Registrar compra', 'mcp-domain', 'register_vehicle_purchase', FALSE, '{}'),
    (3, 'Autorizar financiamiento', 'mcp-domain', 'request_financing_approval', TRUE, '{}'),
    (4, 'Enviar correo', 'mcp-notifications', 'send_purchase_status_email', FALSE, '{}')
) AS s(step_order, name, mcp_target, expected_tool, requires_human, config) ON TRUE
WHERE pt.name = 'Compra con financiamiento'
  AND NOT EXISTS (
      SELECT 1 FROM step_template st
      WHERE st.process_template_id = pt.id AND st.step_order = s.step_order
  );

INSERT INTO process_template (name, description)
SELECT 'Apartado de vehiculo', 'Reserva de un vehículo sin configuración de pago'
WHERE NOT EXISTS (SELECT 1 FROM process_template WHERE name = 'Apartado de vehiculo');

INSERT INTO step_template (process_template_id, step_order, name, mcp_target, expected_tool, requires_human, config)
SELECT pt.id, s.step_order, s.name, s.mcp_target, s.expected_tool, s.requires_human, s.config::jsonb
FROM process_template pt
JOIN (VALUES
    (1, 'Consultar vehículo', 'mcp-domain', 'get_vehicle_information', FALSE, '{"agency_id":"default"}'),
    (2, 'Registrar apartado', 'mcp-domain', 'register_vehicle_purchase', FALSE, '{}'),
    (3, 'Enviar correo', 'mcp-notifications', 'send_purchase_status_email', FALSE, '{}')
) AS s(step_order, name, mcp_target, expected_tool, requires_human, config) ON TRUE
WHERE pt.name = 'Apartado de vehiculo'
  AND NOT EXISTS (
      SELECT 1 FROM step_template st
      WHERE st.process_template_id = pt.id AND st.step_order = s.step_order
  );
