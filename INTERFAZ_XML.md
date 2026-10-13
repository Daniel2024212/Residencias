# Interfaz XML

Abre la carpeta del proyecto en Android Studio. Las pantallas están en app/src/main/res/layout y pueden editarse en Design, Split o Code.

- activity_login.xml: inicio de sesión.
- activity_register.xml: registro de usuario.
- activity_loading.xml: preparación de SQLite.
- activity_admin.xml: administrador.
- activity_user.xml: usuario.
- dialog_create_account.xml: alta de usuarios y administradores.
- dialog_text.xml: reportes, errores y consultas.
- item_image_panel.xml: imágenes, ROI y filtros.
- item_heading.xml, item_processing_info.xml y processing_placeholder.xml: información del procesamiento.

Los estilos están en res/values/ui_styles.xml, los roles en ui_arrays.xml y los fondos en res/drawable/bg_*.xml. Java infla los layouts y conserva eventos, permisos, sesión, consultas, datos y clasificación. Los diálogos de confirmación y lista de cuentas usan los controles estándar de Android.

La cámara sigue abriendo la aplicación de cámara del teléfono, sin que EvoGMD cambie brillo o exposición. Al confirmar la foto comienza el análisis automático. Se conserva el procesamiento interno necesario para ROI y clasificación.

Base: EvoGMD-SQLite-Roles-ROI-Camara-Clasificador-V5. No se modificaron los modelos, bases SQLite ni algoritmos de clasificación o ROI.

Verificación: recursos compilados y enlazados con AAPT2; fuentes Java compiladas con ECJ y android.jar API 35. No se ejecutó en emulador ni teléfono; falta esa comprobación visual y funcional.
