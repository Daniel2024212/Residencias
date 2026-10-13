# EvoGMD: versión XML documentada y depurada

Abre esta carpeta en Android Studio y usa JDK 17 para Gradle. El proyecto declara compileSdk/targetSdk 35 y minSdk 24. Sincroniza Gradle y ejecuta app en un dispositivo Android.

## Acceso y funciones

- Administrador inicial: admin@evogmd.local; contraseña: 1234567890.
- El registro público crea usuarios USER. Un administrador puede crear cuentas USER o ADMIN y promover usuarios.
- Login, roles, SQLite, selección de imágenes, cámara, clasificación y temporizador de sesión se conservan.
- Al confirmar una foto comienza el análisis. EvoGMD no cambia brillo ni exposición de la cámara.
- El usuario ve la imagen y el resultado. El administrador puede consultar ROI, filtros, características, modelos y cuentas.

## Estructura

- app/src/main/java: controladores, autenticación y procesamiento, con comentarios en español.
- app/src/main/res/layout: pantallas y componentes XML.
- app/src/main/res/values y drawable: tema, estilos y fondos usados.
- app/src/main/assets: base SQLite y modelo específico de cámara, incluidos con el proyecto.
- app/src/test: pruebas de los componentes de procesamiento vigentes.
- checks/ModelCompatibilityCheck.java: comprobación JVM de compatibilidad de paquetes serializados.
- third_party: licencia, procedencia y parches del JAR Weka; son necesarios para conservar su información de distribución.

La app carga modelos ya entrenados. Se retiró el código de entrenamiento por carpetas, la optimización evolutiva opcional y los componentes de pantalla antiguos sin uso. No se borraron las imágenes, máscaras o modelos de SQLite.

Consulta GUIA_CODIGO_JAVA_XML.md para el flujo y el índice; INTERFAZ_XML.md para editar pantallas; ADMIN_PREDETERMINADO.md para el alta administrativa; LIMPIEZA.md para conocer los elementos retirados y la verificación.

## PDF y resultados en SQLite

La base se configura automáticamente al iniciar. Cada clasificación crea un PDF y guarda imagen original, nombre, resultado, fecha y usuario en `resultados_clasificacion`, dentro de `evogmd_modelos.db`, la misma base que contiene el catálogo. No requiere Internet, servidor, dirección ni token.

- **Abrir PDF**: visor integrado.
- **Guardar PDF**: elige Descargas u otra carpeta desde el selector Android.
- **Reintentar guardado en SQLite**: recupera registros pendientes si antes faltó espacio o terminó la sesión.

Consulta `PDF_SQLITE.md` para detalles y solución de problemas.
