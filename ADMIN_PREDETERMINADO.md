# EvoGMD-SQLite-Roles con cámara y administrador predeterminado

Base: ZIP original EvoGMD-SQLite-Roles, conservando interfaz por roles, modelo y SQLite.

Administrador incluido automáticamente al crear SQLite o migrar a versión 4:
- Correo: admin@evogmd.local
- Contraseña: 1234567890

El registro público siempre crea USER, incluso el primer registro. El correo del administrador está reservado. La contraseña se almacena mediante PBKDF2 y sal, no en texto plano en SQLite. Estas son credenciales iniciales compartidas para el prototipo.

La migración no borra cuentas ni cambia contraseñas o roles existentes. Una cuenta antigua que ya era ADMIN conserva su rol; el administrador incluido se añade al actualizar. No se requiere desinstalar. Si anteriormente se creó una cuenta con el correo reservado, INSERT OR IGNORE preserva esa cuenta y sus credenciales: se debe resolver ese conflicto de correo antes de usar el acceso predeterminado.

Cámara: pulsar Tomar foto y confirmar para analizar automáticamente. Usuario: original y Baja probabilidad/Alta probabilidad. Administrador: ROI, grises, filtros Gabor, características y funciones de administración. Sigue disponible Seleccionar imagen. Las capturas se guardan en caché privada y corrigen orientación EXIF; no reentrenan el modelo.

Abrir la carpeta del proyecto en Android Studio y sincronizar usando JDK 17. Compilación de fuentes Java verificada con API 35; APK y cámara en dispositivo pendientes. La clasificación es experimental y no equivale a diagnóstico clínico.

## Crear cuentas desde administración
Entrar como administrador > Administrar cuentas > Crear cuenta. Ingresar nombre, correo, contraseña y confirmación; elegir Usuario o Administrador y pulsar Crear. El registro público sigue creando solo usuarios. No se permiten correos duplicados.

La migración a SQLite versión 4 cambia una sola vez la contraseña de admin@evogmd.local (si es ADMIN) a 1234567890, sin modificar las demás cuentas. No desinstalar para conservarlas.
