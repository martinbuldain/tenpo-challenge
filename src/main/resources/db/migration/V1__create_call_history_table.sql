CREATE TABLE IF NOT EXISTS call_history (
    id             BIGSERIAL       PRIMARY KEY,
    endpoint       VARCHAR(255)    NOT NULL,
    http_method    VARCHAR(10)     NOT NULL,
    request_params TEXT,
    response_body  TEXT,
    error_message  TEXT,
    http_status    INTEGER         NOT NULL,
    status         VARCHAR(10)     NOT NULL CHECK (status IN ('SUCCESS', 'ERROR')),
    client_ip      VARCHAR(45),
    created_at     TIMESTAMP       NOT NULL DEFAULT NOW()
);

-- Indice para consultas por fecha (caso de uso: historial ordenado)
CREATE INDEX idx_call_history_created_at ON call_history (created_at DESC);

-- Indice para filtrar por endpoint
CREATE INDEX idx_call_history_endpoint ON call_history (endpoint);

-- Indice para filtrar por estado
CREATE INDEX idx_call_history_status ON call_history (status);

COMMENT ON TABLE  call_history IS 'Registro de todas las llamadas hechas a los endpoints de la API';
COMMENT ON COLUMN call_history.id             IS 'Id unico del registro';
COMMENT ON COLUMN call_history.endpoint       IS 'Path del endpoint invocado';
COMMENT ON COLUMN call_history.http_method    IS 'Metodo HTTP: GET, POST, etc.';
COMMENT ON COLUMN call_history.request_params IS 'Parametros del request serializados';
COMMENT ON COLUMN call_history.response_body  IS 'Body de la respuesta exitosa';
COMMENT ON COLUMN call_history.error_message  IS 'Mensaje de error en caso de respuesta 4xx/5xx';
COMMENT ON COLUMN call_history.http_status    IS 'HTTP status del response';
COMMENT ON COLUMN call_history.status         IS 'Estado logico: SUCCESS (2xx) o ERROR (4xx/5xx)';
COMMENT ON COLUMN call_history.client_ip      IS 'IP del cliente';
COMMENT ON COLUMN call_history.created_at     IS 'Fecha de creacion en UTC de cuando se hizo la llamada';
