-- Esquema de la base preempaquetada. Los datos completos están en app/src/main/assets/evogmd_modelos.db
CREATE TABLE imagenes(id INTEGER PRIMARY KEY AUTOINCREMENT,nombre TEXT NOT NULL,clase TEXT NOT NULL,sha256 TEXT NOT NULL UNIQUE,contenido BLOB NOT NULL,codigo_mascara TEXT NOT NULL REFERENCES mascaras(codigo));

CREATE TABLE mascaras(codigo TEXT PRIMARY KEY, png BLOB NOT NULL);

CREATE TABLE meta(clave TEXT PRIMARY KEY,valor TEXT NOT NULL);

CREATE TABLE modelos(id INTEGER PRIMARY KEY AUTOINCREMENT,nombre TEXT NOT NULL, contenido BLOB NOT NULL, sha256 TEXT NOT NULL, activo INTEGER NOT NULL DEFAULT 0 CHECK(activo IN(0,1)), origen TEXT NOT NULL);

CREATE UNIQUE INDEX un_modelo_activo ON modelos(activo) WHERE activo=1;
