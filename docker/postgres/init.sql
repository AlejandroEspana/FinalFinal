CREATE TABLE IF NOT EXISTS operator_decision_audit (
    id BIGSERIAL PRIMARY KEY,
    alert_id VARCHAR(64) NOT NULL,
    sensor_id VARCHAR(32) NOT NULL,
    chosen_action VARCHAR(64) NOT NULL,
    operator_id VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    routing_target VARCHAR(64) NOT NULL,
    delay_millis BIGINT NOT NULL DEFAULT 0,
    notes VARCHAR(512),
    recorded_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_audit_alert_id ON operator_decision_audit (alert_id);
CREATE INDEX IF NOT EXISTS idx_audit_sensor_id ON operator_decision_audit (sensor_id);
CREATE INDEX IF NOT EXISTS idx_audit_timestamp ON operator_decision_audit (recorded_at);

INSERT INTO operator_decision_audit (alert_id, sensor_id, chosen_action, operator_id, status, routing_target, delay_millis, notes, recorded_at)
VALUES ('ALT-BOOTSTRAP-00', 'sensor_A', 'RECONOCER_Y_ESPERAR_24H', 'SYSTEM_ADMIN', 'INITIALIZED', 'delayed_actions_exchange', 10000, 'Inicialización del clúster SCADA', CURRENT_TIMESTAMP)
ON CONFLICT DO NOTHING;
