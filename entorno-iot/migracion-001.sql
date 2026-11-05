ALTER TABLE lecturas_normales ADD COLUMN IF NOT EXISTS mid VARCHAR(100);
ALTER TABLE lecturas_normales ADD COLUMN IF NOT EXISTS temperatura_c DECIMAL(5, 2);
ALTER TABLE lecturas_normales ADD COLUMN IF NOT EXISTS humedad_pct DECIMAL(5, 2);
ALTER TABLE alertas_criticas ADD COLUMN IF NOT EXISTS mid VARCHAR(100);

CREATE UNIQUE INDEX IF NOT EXISTS uk_lecturas_mid ON lecturas_normales (mid);
CREATE UNIQUE INDEX IF NOT EXISTS uk_alertas_mid ON alertas_criticas (mid);

-- El control por nodo + momento pasa a aplicar solo a mensajes sin mid
ALTER TABLE lecturas_normales DROP CONSTRAINT IF EXISTS uk_nodo_timestamp_origen;
CREATE UNIQUE INDEX IF NOT EXISTS uk_lecturas_nodo_ts_sin_mid
    ON lecturas_normales (nodo_id, timestamp_origen) WHERE mid IS NULL;