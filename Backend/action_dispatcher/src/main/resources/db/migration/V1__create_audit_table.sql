CREATE TABLE IF NOT EXISTS operator_decision_audit (
    id BIGSERIAL PRIMARY KEY,
    alert_id VARCHAR(100) NOT NULL,
    sensor_id VARCHAR(64) NOT NULL,
    chosen_action VARCHAR(80) NOT NULL,
    operator_id VARCHAR(80) NOT NULL,
    routing_target VARCHAR(120) NOT NULL,
    delay_ms BIGINT NOT NULL DEFAULT 0,
    notes VARCHAR(500),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
