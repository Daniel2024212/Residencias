# Limpieza del proyecto

Se retiraron componentes que no participan en la aplicación actual. Se mantienen login, roles, sesión, captura, selección de imágenes, consulta de cuentas, importación de modelos y clasificación.

## Elementos retirados

- app/src/main/java/com/example/evogmd/dataset/PreparadorDataset.java
- app/src/main/java/com/example/evogmd/dataset/DivisionTrainTest.java
- app/src/main/java/com/example/evogmd/dataset/MuestraDermatologica.java
- app/src/main/java/com/example/evogmd/clasificacion/ProcesadorLote.java
- app/src/main/java/com/example/evogmd/clasificacion/DifferentialEvolution.java
- app/src/main/java/com/example/evogmd/clasificacion/FitnessEvaluator.java
- app/src/main/java/com/example/evogmd/clasificacion/EvoGMD.java
- app/src/main/java/com/example/evogmd/imagen/MarcoImagen.java
- app/src/main/java/com/example/evogmd/imagen/PanelImagen.java
- app/src/test/java/com/example/evogmd/dataset/DivisionTrainTestTest.java
- app/src/main/res/drawable/panel_border.xml
- app/src/main/res/values/colors.xml
- CORRECCION_CLASIFICADOR_CAMARA_V5.md
- CORRECCION_CAMARA_ROI_V2.md
- CORRECCION_ROI_CAMARA_V3.md
- CORRECCION_SESION_CAMARA_V4.md
- CORRECCION_ROI_CAMARA.md
- SESION_Y_ROI.md
- VERIFICACION_NUCLEO.json

## Código simplificado

Se retiraron rutinas de entrenamiento y validación cruzada de ClasificadorSVM, sus auxiliares y constantes; el selector de carpetas; alias antiguos de conversión de imágenes; needsAdmin; el candidato ROI histórico no invocado y el descriptor de parámetros evolutivos. Se eliminó la dependencia DocumentFile y las cadenas del entrenamiento anterior. Los métodos activos conservan sus comentarios.

Se mantienen MainActivity y VentanaActivity como redirecciones de compatibilidad al login. Se mantienen ambas clases ModeloPersistido: sus nombres, campos y serialVersionUID son necesarios para leer los modelos existentes. También se mantienen las pruebas del procesamiento, los esquemas SQL y la licencia y parches de Weka.

## Verificación

- Compilación de las fuentes principales Java con ECJ y Android API 35.
- Compilación y enlace de recursos XML con AAPT2.
- Los bytes de ambos assets (SQLite y modelo de cámara) son idénticos a los de la versión documentada.
- Prueba de compatibilidad de ambos modelos: 16 vectores por modelo; etiquetas, distribuciones y reportes coinciden con la lectura directa del modelo anterior.
- Aceptación del paquete Android serializado y rechazo de bytes corruptos.
- Se corrigió el comprobador JVM para elegir el constructor por firma en vez del primer constructor reflejado, que puede ser sintético; ahora también comprueba la ruta específica de cámara.

No se ha ejecutado esta entrega en un teléfono ni se ha recalculado accuracy con un dataset. La limpieza no constituye una validación clínica.
