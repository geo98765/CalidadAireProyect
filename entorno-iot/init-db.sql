CREATE TABLE IF NOT EXISTS nodos_sensores (
    id UUID PRIMARY KEY,
    mac_address VARCHAR(50) UNIQUE,
    ubicacion VARCHAR(100),
    estatus BOOLEAN DEFAULT TRUE
);

CREATE TABLE IF NOT EXISTS lecturas_normales (
    id BIGSERIAL PRIMARY KEY,
    mid VARCHAR(100),
    nodo_id UUID REFERENCES nodos_sensores(id),
    co2_ppm DECIMAL(8, 2),
    pm25_ugm3 DECIMAL(8, 2),
    pm10_ugm3 DECIMAL(8, 2),
    temperatura_c DECIMAL(5, 2),
    humedad_pct DECIMAL(5, 2),
    timestamp_origen TIMESTAMP WITH TIME ZONE NOT NULL,
    timestamp_recepcion TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    nivel_riesgo VARCHAR(20)
);


CREATE UNIQUE INDEX IF NOT EXISTS uk_lecturas_mid ON lecturas_normales (mid);
CREATE UNIQUE INDEX IF NOT EXISTS uk_lecturas_nodo_ts_sin_mid
    ON lecturas_normales (nodo_id, timestamp_origen) WHERE mid IS NULL;

CREATE TABLE IF NOT EXISTS alertas_criticas (
    id BIGSERIAL PRIMARY KEY,
    mid VARCHAR(100),
    nodo_id UUID REFERENCES nodos_sensores(id),
    tipo_alerta VARCHAR(50) NOT NULL,
    valor_registrado DECIMAL(8, 2) NOT NULL,
    mensaje TEXT,
    timestamp_origen TIMESTAMP WITH TIME ZONE NOT NULL,
    estado_notificacion BOOLEAN DEFAULT FALSE
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_alertas_mid ON alertas_criticas (mid);