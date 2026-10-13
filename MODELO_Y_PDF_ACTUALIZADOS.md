# Cambios

- Modelo para foto y galería: `app/src/main/assets/modelo_svm_best_java8_v1.dat` (archivo suministrado por el usuario). Se carga en ambos flujos, nunca V5. Es un modelo ya entrenado, no hay reentrenamiento nuevo.
- Usuario: guarda automáticamente en `getFilesDir()/ResultadosPDF/usuario_<id>/`.
- Administrador: al pulsar **Guardar PDF** guarda una copia en `getFilesDir()/ResultadosPDF/usuario_<id>/`. No abre un selector de documentos.
- Los reportes originales permanecen dentro de `getFilesDir()/reports/<id>/`. La carpeta ResultadosPDF es privada de Android.

**Advertencia:** Un modelo V1 entrenado con máscaras expertas puede ser incompatible estadísticamente con máscaras automáticas de una cámara. Corregir una etiqueta errónea requiere contrastar etiquetas reales, dimensiones, ROI, orden y distribución de características. No se puede garantizar benignidad con un cambio de archivo, ni inferir diagnóstico de una imagen aislada.
