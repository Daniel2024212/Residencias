# PDF y SQLite local

## Configuración

No hay que escribir servidor, usuario SQL ni token. Al iniciar se copia evogmd_modelos.db si todavía no existe. En nuevas instalaciones y actualizaciones se crea automáticamente resultados_clasificacion con CREATE TABLE IF NOT EXISTS. No se elimina ni reemplaza el catálogo instalado.

La base está en el almacenamiento privado de Android, en databases/evogmd_modelos.db. Las cuentas permanecen en evogmd_login.db. En Android Studio puedes revisar las tablas con App Inspection / Database Inspector mientras ejecutas la app en un dispositivo compatible. No debes ejecutar manualmente el script incluido para usar la app.

## Qué se guarda

Cada resultado contiene UUID, ID del usuario local, nombre del archivo, clasificación Baja probabilidad/Alta probabilidad, imagen original BLOB, SHA-256, tipo de imagen, fecha y ruta privada del PDF. La imagen es una copia de los bytes originales. El PDF usa la imagen decodificada y orientada que participó en el análisis. Las predicciones se guardan aparte de las etiquetas y máscaras del catálogo; no se usan para entrenar.

El PDF permanece como archivo privado en files/reports/ID_USUARIO/UUID/resultado.pdf. No está dentro de un BLOB: su ruta se registra en SQLite. La imagen sí queda dentro de la base. La copia privada permite reintentar si SQLite falla. Solo el usuario autenticado puede guardar los pendientes de su propia carpeta. Repetir el guardado del mismo UUID no duplica el registro.

## Abrir y guardar PDF

1. Clasifica una imagen o confirma una foto.
2. Espera a que se genere el reporte.
3. Usa Abrir PDF para verlo dentro de la app. Incluye imagen, nombre del archivo, resultado, fecha e identificador.
4. Usa Guardar PDF para exportarlo a Descargas u otra carpeta elegida en Android.

La aplicación verifica la cabecera y las páginas del PDF mediante PdfRenderer. Al exportar, cierra el archivo de destino antes de indicar éxito. Guarda el UUID de exportación en el estado de la actividad para recuperarlo si Android recrea la pantalla del selector. Si falla la escritura, intenta eliminar el destino incompleto.

Un PDF vacío o dañado exportado por una versión anterior no se repara automáticamente: vuelve a clasificar y genera un archivo nuevo. Si un visor externo falla pero Abrir PDF funciona, exporta de nuevo a Descargas y prueba ese nuevo archivo.

## Fallos de almacenamiento

Si falta espacio, la sesión caduca o la imagen supera el límite de guardado de 32 MiB, se muestra Guardado pendiente. El PDF puede seguir disponible. Libera espacio o vuelve a iniciar sesión y pulsa Reintentar guardado en SQLite. Borrar datos o desinstalar la app elimina su SQLite y reportes privados; los PDF exportados a una carpeta elegida son independientes.

No hay envío a nube ni acceso desde otros teléfonos. SQLite en esta app es almacenamiento local.

## Verificación

Se compilan las fuentes Java y se enlazan los recursos Android. Se comprueba el esquema SQLite sobre una copia del catálogo: creación repetida sin pérdida, persistencia BLOB, rechazo de etiquetas inválidas y UUID único. No se ha probado la interfaz ni el PDF en un teléfono en este entorno. La recuperación tras recreación y el visor necesitan esa prueba funcional.
