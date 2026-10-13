# Dependencia Weka Android

`app/libs/weka-android-3.8.1.jar` procede del proyecto
`MeiyappanKannappa/Weka-Android`, que elimina componentes Swing/AWT de Weka
para permitir su uso en Android. La base corresponde a Weka 3.8.1.

- Repositorio: https://github.com/MeiyappanKannappa/Weka-Android
- Weka: https://www.cs.waikato.ac.nz/ml/weka/
- Licencia: GNU General Public License v3 o posterior.

El JAR se incluye localmente porque `weka-stable` de escritorio no puede
usarse directamente en Android.

## Ajuste Android aplicado

El port 3.8.1 todavía inicializaba el gestor de paquetes de escritorio desde
`weka.core.Utils`, lo cual intentaba cargar `org.bounce` y clases AWT/Swing al
crear `SMO`. El JAR incluido reemplaza `Utils.class` por la versión producida
con `weka-android-utils.patch`. El cambio conserva la lectura de propiedades,
pero utiliza `user.home/wekafiles` directamente y evita inicializar el gestor
gráfico de paquetes, que no interviene en entrenamiento o predicción.

También reemplaza `weka.classifiers.evaluation.Evaluation.class` con
`weka-android-evaluation.patch`: la consulta opcional de `globalInfo()` usa
`java.lang.reflect.Method` en vez de `java.beans`, paquete ausente en Android.

Los parches reproducibles están en esta misma carpeta. Si se distribuye la
aplicación, deben respetarse las obligaciones de GPLv3.


Actualización de acceso: el registro siempre crea USER. El administrador predeterminado es admin@evogmd.local / 1234567890. Consultar ADMIN_PREDETERMINADO.md; esta regla sustituye cualquier instrucción anterior sobre el primer registro.
