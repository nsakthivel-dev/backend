-- ==============================================================================
-- VoltGrid EV Platform - V1 Initial PostgreSQL Schema Migration
-- Compatible with Supabase PostgreSQL (15 / 16) and standard PostgreSQL
-- ==============================================================================

-- 1. Users Table
CREATE TABLE IF NOT EXISTS users (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(100) NOT NULL UNIQUE,
    email VARCHAR(150) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    full_name VARCHAR(100) NOT NULL,
    phone VARCHAR(30),
    role VARCHAR(30) NOT NULL,
    assigned_shop_id BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 2. Transformers Table
CREATE TABLE IF NOT EXISTS transformers (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    location VARCHAR(150) NOT NULL,
    nameplate_rating_kva DOUBLE PRECISION NOT NULL,
    power_factor DOUBLE PRECISION NOT NULL DEFAULT 0.95,
    approved_limit_kw DOUBLE PRECISION NOT NULL,
    safety_margin_percent DOUBLE PRECISION NOT NULL DEFAULT 10.0,
    non_ev_baseline_kw DOUBLE PRECISION NOT NULL DEFAULT 75.0,
    status VARCHAR(30) NOT NULL DEFAULT 'OPERATIONAL',
    updated_by VARCHAR(100) DEFAULT 'UTILITY_SYSTEM',
    last_updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 3. Transformer Risk Configurations Table
CREATE TABLE IF NOT EXISTS transformer_risk_configs (
    id BIGSERIAL PRIMARY KEY,
    transformer_id BIGINT NOT NULL REFERENCES transformers(id) ON DELETE CASCADE,
    config_version INT NOT NULL DEFAULT 1,
    approved_limit_kw DOUBLE PRECISION NOT NULL,
    warning_threshold_percent DOUBLE PRECISION NOT NULL DEFAULT 90.0,
    critical_threshold_percent DOUBLE PRECISION NOT NULL DEFAULT 98.0,
    safety_margin_percent DOUBLE PRECISION NOT NULL DEFAULT 10.0,
    non_ev_baseline_kw DOUBLE PRECISION NOT NULL DEFAULT 75.0,
    high_risk_policy VARCHAR(100) DEFAULT 'RESTRICT_NEW_ONLINE_BOOKINGS,OFFER_ALTERNATIVES',
    critical_risk_policy VARCHAR(100) DEFAULT 'EMERGENCY_THROTTLING,PROHIBIT_NEW_DEMAND',
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    effective_from TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(100) NOT NULL DEFAULT 'UTILITY_ADMIN',
    change_reason VARCHAR(500) DEFAULT 'Initial baseline configuration'
);
CREATE INDEX IF NOT EXISTS idx_risk_config_transformer ON transformer_risk_configs(transformer_id, is_active);

-- 4. Shops Table
CREATE TABLE IF NOT EXISTS shops (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    location VARCHAR(150) NOT NULL,
    transformer_id BIGINT NOT NULL REFERENCES transformers(id) ON DELETE RESTRICT,
    operating_hours VARCHAR(50) DEFAULT '24/7',
    contact_phone VARCHAR(30),
    allocated_capacity_kw DOUBLE PRECISION NOT NULL DEFAULT 150.0,
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 5. Stations Table
CREATE TABLE IF NOT EXISTS stations (
    id BIGSERIAL PRIMARY KEY,
    shop_id BIGINT NOT NULL REFERENCES shops(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    address VARCHAR(200) NOT NULL,
    latitude DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL,
    pricing_floor_per_kwh DOUBLE PRECISION NOT NULL DEFAULT 0.20,
    pricing_cap_per_kwh DOUBLE PRECISION NOT NULL DEFAULT 0.65,
    base_tariff_per_kwh DOUBLE PRECISION NOT NULL DEFAULT 0.35,
    turnaround_buffer_minutes INT NOT NULL DEFAULT 10,
    status VARCHAR(30) NOT NULL DEFAULT 'ONLINE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 6. Chargers Table
CREATE TABLE IF NOT EXISTS chargers (
    id BIGSERIAL PRIMARY KEY,
    station_id BIGINT NOT NULL REFERENCES stations(id) ON DELETE CASCADE,
    code VARCHAR(50) NOT NULL,
    model VARCHAR(100),
    rated_power_kw DOUBLE PRECISION NOT NULL DEFAULT 50.0,
    supported_control_capabilities VARCHAR(150) DEFAULT 'DYNAMIC_POWER_CURTAILMENT,SMART_THROTTLING',
    status VARCHAR(30) NOT NULL DEFAULT 'OPERATIONAL',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 7. Connectors Table
CREATE TABLE IF NOT EXISTS connectors (
    id BIGSERIAL PRIMARY KEY,
    charger_id BIGINT NOT NULL REFERENCES chargers(id) ON DELETE CASCADE,
    connector_number INT NOT NULL,
    connector_type VARCHAR(30) NOT NULL DEFAULT 'CCS2',
    max_power_kw DOUBLE PRECISION NOT NULL DEFAULT 50.0,
    is_walk_in_reserve BOOLEAN NOT NULL DEFAULT FALSE,
    status VARCHAR(30) NOT NULL DEFAULT 'AVAILABLE',
    version BIGINT DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 8. Vehicles Table
CREATE TABLE IF NOT EXISTS vehicles (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    make VARCHAR(50) NOT NULL,
    model VARCHAR(50) NOT NULL,
    license_plate VARCHAR(30) NOT NULL UNIQUE,
    battery_capacity_kwh DOUBLE PRECISION,
    max_charging_rate_kw DOUBLE PRECISION,
    connector_type VARCHAR(30) NOT NULL,
    current_soc_percent INT NOT NULL DEFAULT 30,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 9. Bookings Table
CREATE TABLE IF NOT EXISTS bookings (
    id BIGSERIAL PRIMARY KEY,
    booking_reference VARCHAR(64) NOT NULL UNIQUE,
    customer_id BIGINT NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    vehicle_id BIGINT NOT NULL REFERENCES vehicles(id) ON DELETE RESTRICT,
    connector_id BIGINT NOT NULL REFERENCES connectors(id) ON DELETE RESTRICT,
    start_time TIMESTAMP NOT NULL,
    end_time TIMESTAMP NOT NULL,
    estimated_charging_duration_minutes INT NOT NULL,
    turnaround_buffer_minutes INT NOT NULL DEFAULT 10,
    initial_soc INT NOT NULL,
    target_soc INT NOT NULL,
    booked_price_per_kwh DOUBLE PRECISION NOT NULL,
    total_estimated_cost DOUBLE PRECISION NOT NULL,
    allocated_power_kw DOUBLE PRECISION NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'CONFIRMED',
    is_walk_in BOOLEAN NOT NULL DEFAULT FALSE,
    is_urgent BOOLEAN NOT NULL DEFAULT FALSE,
    version BIGINT DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_booking_connector_time ON bookings(connector_id, start_time, end_time);
CREATE INDEX IF NOT EXISTS idx_booking_customer ON bookings(customer_id);
CREATE INDEX IF NOT EXISTS idx_booking_status ON bookings(status);

-- 10. Charging Sessions Table
CREATE TABLE IF NOT EXISTS charging_sessions (
    id BIGSERIAL PRIMARY KEY,
    session_reference VARCHAR(64) NOT NULL UNIQUE,
    booking_id BIGINT REFERENCES bookings(id) ON DELETE SET NULL,
    connector_id BIGINT NOT NULL REFERENCES connectors(id) ON DELETE RESTRICT,
    vehicle_id BIGINT NOT NULL REFERENCES vehicles(id) ON DELETE RESTRICT,
    actual_start_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actual_end_time TIMESTAMP,
    metered_kwh DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    current_power_kw DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    current_soc_percent INT NOT NULL DEFAULT 30,
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    billed_amount DOUBLE PRECISION DEFAULT 0.0,
    session_notes VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_session_status ON charging_sessions(status);
CREATE INDEX IF NOT EXISTS idx_session_connector ON charging_sessions(connector_id);

-- 11. Meter Readings Table
CREATE TABLE IF NOT EXISTS meter_readings (
    id BIGSERIAL PRIMARY KEY,
    transformer_id BIGINT NOT NULL REFERENCES transformers(id) ON DELETE CASCADE,
    shop_id BIGINT REFERENCES shops(id) ON DELETE SET NULL,
    active_power_kw DOUBLE PRECISION NOT NULL,
    apparent_power_kva DOUBLE PRECISION NOT NULL,
    power_factor DOUBLE PRECISION NOT NULL DEFAULT 0.95,
    reading_timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    source VARCHAR(30) NOT NULL DEFAULT 'SIMULATED',
    data_quality VARCHAR(30) NOT NULL DEFAULT 'VALID'
);
CREATE INDEX IF NOT EXISTS idx_meter_transformer_time ON meter_readings(transformer_id, reading_timestamp);

-- 12. Load Forecasts Table
CREATE TABLE IF NOT EXISTS load_forecasts (
    id BIGSERIAL PRIMARY KEY,
    transformer_id BIGINT NOT NULL REFERENCES transformers(id) ON DELETE CASCADE,
    forecast_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    target_interval_start TIMESTAMP NOT NULL,
    target_interval_end TIMESTAMP NOT NULL,
    baseline_load_kw DOUBLE PRECISION NOT NULL,
    active_sessions_load_kw DOUBLE PRECISION NOT NULL,
    bookings_load_kw DOUBLE PRECISION NOT NULL,
    expected_walk_in_load_kw DOUBLE PRECISION NOT NULL,
    total_predicted_load_kw DOUBLE PRECISION NOT NULL,
    upper_prediction_bound_kw DOUBLE PRECISION NOT NULL,
    approved_limit_kw DOUBLE PRECISION NOT NULL,
    risk_status VARCHAR(30) NOT NULL DEFAULT 'NORMAL',
    confidence_score DOUBLE PRECISION NOT NULL DEFAULT 0.95
);
CREATE INDEX IF NOT EXISTS idx_forecast_transformer_interval ON load_forecasts(transformer_id, target_interval_start);

-- 13. Capacity Allocations Table
CREATE TABLE IF NOT EXISTS capacity_allocations (
    id BIGSERIAL PRIMARY KEY,
    transformer_id BIGINT NOT NULL REFERENCES transformers(id) ON DELETE CASCADE,
    shop_id BIGINT NOT NULL REFERENCES shops(id) ON DELETE CASCADE,
    interval_start TIMESTAMP NOT NULL,
    interval_end TIMESTAMP NOT NULL,
    allocated_kw DOUBLE PRECISION NOT NULL,
    requested_kw DOUBLE PRECISION NOT NULL,
    approved_by VARCHAR(100) DEFAULT 'SYSTEM_ALLOCATOR',
    status VARCHAR(30) NOT NULL DEFAULT 'APPROVED',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_allocation_shop_interval ON capacity_allocations(shop_id, interval_start, interval_end);
CREATE INDEX IF NOT EXISTS idx_allocation_transformer_interval ON capacity_allocations(transformer_id, interval_start);

-- 14. Capacity Requests Table
CREATE TABLE IF NOT EXISTS capacity_requests (
    id BIGSERIAL PRIMARY KEY,
    transformer_id BIGINT NOT NULL REFERENCES transformers(id) ON DELETE CASCADE,
    shop_id BIGINT NOT NULL REFERENCES shops(id) ON DELETE CASCADE,
    requested_additional_kw DOUBLE PRECISION NOT NULL,
    reason VARCHAR(255) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    requested_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reviewed_at TIMESTAMP,
    reviewed_by VARCHAR(100)
);

-- 15. Price Quotes Table
CREATE TABLE IF NOT EXISTS price_quotes (
    id BIGSERIAL PRIMARY KEY,
    quote_token VARCHAR(64) NOT NULL UNIQUE,
    station_id BIGINT NOT NULL REFERENCES stations(id) ON DELETE CASCADE,
    slot_start TIMESTAMP NOT NULL,
    slot_end TIMESTAMP NOT NULL,
    base_tariff DOUBLE PRECISION NOT NULL,
    congestion_index DOUBLE PRECISION NOT NULL,
    calculated_price_per_kwh DOUBLE PRECISION NOT NULL,
    floor_price DOUBLE PRECISION NOT NULL,
    cap_price DOUBLE PRECISION NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_quote_token ON price_quotes(quote_token);

-- 16. Notifications Table
CREATE TABLE IF NOT EXISTS notifications (
    id BIGSERIAL PRIMARY KEY,
    recipient_user_id BIGINT REFERENCES users(id) ON DELETE CASCADE,
    recipient_role VARCHAR(30),
    title VARCHAR(150) NOT NULL,
    message VARCHAR(1000) NOT NULL,
    severity VARCHAR(20) NOT NULL DEFAULT 'INFO',
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    related_entity_id BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_notification_user ON notifications(recipient_user_id);
CREATE INDEX IF NOT EXISTS idx_notification_role ON notifications(recipient_role);

-- 17. Audit Logs Table
CREATE TABLE IF NOT EXISTS audit_logs (
    id BIGSERIAL PRIMARY KEY,
    timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actor_username VARCHAR(100) NOT NULL,
    actor_role VARCHAR(50) NOT NULL,
    action VARCHAR(100) NOT NULL,
    entity_name VARCHAR(100),
    entity_id BIGINT,
    details VARCHAR(2000),
    ip_address VARCHAR(50)
);
CREATE INDEX IF NOT EXISTS idx_audit_entity ON audit_logs(entity_name, entity_id);
CREATE INDEX IF NOT EXISTS idx_audit_timestamp ON audit_logs(timestamp);
