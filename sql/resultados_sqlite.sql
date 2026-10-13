-- Referencia: la aplicación ejecuta esta migración automáticamente. No borra datos existentes.
CREATE TABLE IF NOT EXISTS resultados_clasificacion (id TEXT PRIMARY KEY, usuario_id INTEGER NOT NULL, nombre_archivo TEXT NOT NULL, clasificacion TEXT NOT NULL CHECK(clasificacion IN ('Baja probabilidad','Alta probabilidad')), imagen BLOB NOT NULL, imagen_sha256 TEXT NOT NULL, tipo_imagen TEXT NOT NULL, fecha TEXT NOT NULL, pdf_ruta TEXT NOT NULL);
CREATE INDEX IF NOT EXISTS idx_resultados_usuario_fecha ON resultados_clasificacion(usuario_id,fecha);
