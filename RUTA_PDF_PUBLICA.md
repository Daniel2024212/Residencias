# Ubicación pública de los PDF

Los PDF se guardan ahora en **Almacenamiento interno > Documentos (Documents) > EvoGMD > ResultadosPDF**.

- **Usuario:** guardado automático al terminar cada clasificación.
- **Administrador:** pulsa **Guardar PDF** cuando desee conservar una copia.
- Android 10 o posterior: se utiliza MediaStore, sin permisos de almacenamiento adicionales.
- Android 7 a 9: se solicita permiso de almacenamiento para escribir en Documentos.
- Cada PDF contiene el ID del resultado, para evitar generar copias al repetir el guardado.
- El reporte original y los metadatos necesarios para SQLite permanecen en el espacio privado de la aplicación.
- El PDF público puede localizarse usando la aplicación Archivos del teléfono; si no aparece inmediatamente, cierra y vuelve a abrir Archivos.
