-- SQLiteOpenHelper crea esta tabla automáticamente en evogmd_login.db.
CREATE TABLE usuarios (
 id INTEGER PRIMARY KEY AUTOINCREMENT,
 nombre TEXT NOT NULL,
 correo TEXT NOT NULL UNIQUE,
 password_hash BLOB NOT NULL,
 salt BLOB NOT NULL,
 iteraciones INTEGER NOT NULL,
 rol TEXT NOT NULL DEFAULT 'USER' CHECK(rol IN ('ADMIN','USER'))
);
