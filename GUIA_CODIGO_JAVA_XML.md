# Guía del código Java y XML de EvoGMD

La documentación se encuentra dentro de los archivos: Javadoc en los métodos Java y comentarios XML en las pantallas y recursos. Esta edición añade comentarios; no cambia instrucciones ejecutables, valores de recursos, bases de datos ni modelos.

## Flujo actual

1. LoginActivity abre SQLite y valida las credenciales mediante DatabaseHelper y PasswordHasher.
2. La cuenta determina la pantalla: AdminActivity o UsuarioActivity, ambas controladas por AnalysisActivity.
3. La selección de archivo o la captura produce una URI. La cámara externa conserva sus ajustes normales; EvoGMD no regula brillo ni exposición.
4. ModelDatabase busca una máscara por el hash exacto del archivo. Si no existe, Imagen calcula una ROI automática.
5. Imagen redimensiona a 720 × 480, convierte a grises y aplica cuatro filtros Gabor. MSGFB genera ocho valores: media y desviación por filtro.
6. Las fotos sin máscara experta usan el modelo específico de cámara; los demás casos usan el modelo general. La interfaz traduce Nevus a Baja probabilidad y Melanoma a Alta probabilidad.
7. El administrador puede consultar las imágenes intermedias; el usuario ve la imagen y el resultado.

## Convenciones de lectura

- Las matrices se indexan como `[fila][columna]`, equivalentes a `[y][x]`.
- Las máscaras empaquetadas usan blanco para lesión y negro para fondo; los auxiliares morfológicos emplean booleanos.
- La normalización visual de respuestas Gabor sirve para mostrarlas; no es una modificación del brillo de la foto capturada.
- `res/layout` define la estructura visual, `res/drawable` los fondos y `res/values` los estilos y otros recursos.
- Las operaciones de base de datos, decodificación y clasificación del controlador se ejecutan en un executor; las vistas se actualizan en el hilo principal.

## Sesión y almacenamiento

La sesión tiene un límite de cinco minutos. La implementación conserva identificador y marca de interacción en SharedPreferences. El método touch actualiza la marca si existe usuario; no comprueba por sí mismo que el plazo anterior siga vigente. El regreso de cámara renueva esa marca antes de comprobar la sesión. Por ello, documentar este código no equivale a cambiar ni endurecer su política de expiración.

evogmd_login.db contiene cuentas, roles y hashes de contraseña. evogmd_modelos.db contiene el modelo general y el catálogo de imágenes y máscaras. El modelo específico de cámara se carga desde assets. Importar un modelo general no reemplaza el modelo de cámara.

## Código conservado por compatibilidad

PreparadorDataset, ProcesadorLote, DivisionTrainTest y las rutinas de entrenamiento siguen presentes, pero las pantallas XML actuales no ofrecen entrenamiento por carpetas. DifferentialEvolution y FitnessEvaluator son módulos opcionales. El paquete clasificacion externo conserva la estructura serializable del modelo de escritorio.

## Índice Java

| Archivo | Responsabilidad | Métodos documentados |
|---|---|---:|
| [app/src/main/java/clasificacion/ClasificadorSVM.java](app/src/main/java/clasificacion/ClasificadorSVM.java) | Puente de compatibilidad de serialización con el paquete Java de escritorio. Conservar nombre de paquete, clases y serialVersionUID para leer modelos existentes. | 0 |
| [app/src/main/java/com/example/evogmd/UsuarioActivity.java](app/src/main/java/com/example/evogmd/UsuarioActivity.java) | Selecciona el rol USER; AnalysisActivity aplica la autorización y carga activity_user.xml. | 1 |
| [app/src/main/java/com/example/evogmd/VentanaActivity.java](app/src/main/java/com/example/evogmd/VentanaActivity.java) | Entrada antigua que redirige al login para respetar el flujo por roles. | 1 |
| [app/src/main/java/com/example/evogmd/AdminActivity.java](app/src/main/java/com/example/evogmd/AdminActivity.java) | Selecciona el rol ADMIN; AnalysisActivity aplica la autorización y carga activity_admin.xml. | 1 |
| [app/src/main/java/com/example/evogmd/MainActivity.java](app/src/main/java/com/example/evogmd/MainActivity.java) | Entrada de compatibilidad que redirige al login. | 1 |
| [app/src/main/java/com/example/evogmd/AnalysisActivity.java](app/src/main/java/com/example/evogmd/AnalysisActivity.java) | Controlador compartido de las pantallas de administrador y usuario. Coordina XML, sesión, captura, trabajo en segundo plano y presentación de predicciones. | 29 |
| [app/src/main/java/com/example/evogmd/dataset/MuestraDermatologica.java](app/src/main/java/com/example/evogmd/dataset/MuestraDermatologica.java) | Objeto de datos que reúne origen, nombre, etiqueta y vector de una muestra. | 6 |
| [app/src/main/java/com/example/evogmd/dataset/PreparadorDataset.java](app/src/main/java/com/example/evogmd/dataset/PreparadorDataset.java) | Utilidades históricas para recorrer datasets de carpetas mediante Storage Access Framework. | 17 |
| [app/src/main/java/com/example/evogmd/dataset/DivisionTrainTest.java](app/src/main/java/com/example/evogmd/dataset/DivisionTrainTest.java) | Construye la partición estratificada 80/20 usada por el experimento histórico. | 9 |
| [app/src/main/java/com/example/evogmd/descriptor/MSGFB.java](app/src/main/java/com/example/evogmd/descriptor/MSGFB.java) | Banco multiescala de cuatro filtros Gabor; produce ocho características en orden media, desviación por filtro. | 12 |
| [app/src/main/java/com/example/evogmd/clasificacion/ClasificadorSVM.java](app/src/main/java/com/example/evogmd/clasificacion/ClasificadorSVM.java) | Gestiona modelos Weka, predicción, compatibilidad de paquetes y rutinas históricas de entrenamiento. | 36 |
| [app/src/main/java/com/example/evogmd/clasificacion/ProcesadorLote.java](app/src/main/java/com/example/evogmd/clasificacion/ProcesadorLote.java) | Punto de entrada histórico del experimento por carpetas, conservado por compatibilidad. | 2 |
| [app/src/main/java/com/example/evogmd/clasificacion/EvoGMD.java](app/src/main/java/com/example/evogmd/clasificacion/EvoGMD.java) | Representa un candidato evolutivo de cuatro filtros y seis parámetros por filtro. | 11 |
| [app/src/main/java/com/example/evogmd/clasificacion/FitnessEvaluator.java](app/src/main/java/com/example/evogmd/clasificacion/FitnessEvaluator.java) | Evalúa candidatos con descriptores Gabor y validación cruzada SVM. | 5 |
| [app/src/main/java/com/example/evogmd/clasificacion/DifferentialEvolution.java](app/src/main/java/com/example/evogmd/clasificacion/DifferentialEvolution.java) | Optimizador opcional DE/best/1/bin; no se ejecuta al tomar una foto. | 7 |
| [app/src/main/java/com/example/evogmd/filtrogabor/FiltroGabor.java](app/src/main/java/com/example/evogmd/filtrogabor/FiltroGabor.java) | Implementa kernels, convolución, estadísticas y normalización exclusiva para visualización. | 7 |
| [app/src/main/java/com/example/evogmd/transformaciones/Binarizacion.java](app/src/main/java/com/example/evogmd/transformaciones/Binarizacion.java) | Aplica la máscara ROI sobre la imagen gris para aislar la lesión. | 3 |
| [app/src/main/java/com/example/evogmd/transformaciones/ParametrosGabor.java](app/src/main/java/com/example/evogmd/transformaciones/ParametrosGabor.java) | Contiene los bancos BEST, MEDIAN y WORST y devuelve copias para proteger sus constantes. | 3 |
| [app/src/main/java/com/example/evogmd/transformaciones/RecortarROI.java](app/src/main/java/com/example/evogmd/transformaciones/RecortarROI.java) | Calcula máscaras heurísticas; dispone de rutas diferentes para fotos de cámara e imágenes generales. No es un modelo de segmentación entrenado. | 20 |
| [app/src/main/java/com/example/evogmd/transformaciones/Grises.java](app/src/main/java/com/example/evogmd/transformaciones/Grises.java) | Calcula grises mediante el promedio aritmético de los canales RGB. | 2 |
| [app/src/main/java/com/example/evogmd/transformaciones/TransImgToMatriz.java](app/src/main/java/com/example/evogmd/transformaciones/TransImgToMatriz.java) | Convierte entre Bitmap y matrices ARGB indexadas como [fila][columna]. | 6 |
| [app/src/main/java/com/example/evogmd/login/PasswordHasher.java](app/src/main/java/com/example/evogmd/login/PasswordHasher.java) | Deriva y verifica contraseñas con PBKDF2 y sal aleatoria; no almacena contraseñas en texto plano. | 4 |
| [app/src/main/java/com/example/evogmd/login/Session.java](app/src/main/java/com/example/evogmd/login/Session.java) | Gestiona la sesión local persistida y el límite de cinco minutos; los callbacks de actividad determinan cuándo cerrar o renovar la sesión. | 10 |
| [app/src/main/java/com/example/evogmd/login/LoginActivity.java](app/src/main/java/com/example/evogmd/login/LoginActivity.java) | Controlador de login y registro. Valida los formularios XML y delega credenciales y roles a SQLite. | 9 |
| [app/src/main/java/com/example/evogmd/login/DatabaseHelper.java](app/src/main/java/com/example/evogmd/login/DatabaseHelper.java) | Gestiona evogmd_login.db versión 4, usuarios, hashes de contraseñas y autorización de operaciones administrativas. | 15 |
| [app/src/main/java/com/example/evogmd/storage/ModelDatabase.java](app/src/main/java/com/example/evogmd/storage/ModelDatabase.java) | Instala y consulta evogmd_modelos.db desde assets; almacena modelos serializados y relaciona imágenes con máscaras por hash. | 15 |
| [app/src/main/java/com/example/evogmd/imagen/Imagen.java](app/src/main/java/com/example/evogmd/imagen/Imagen.java) | Coordina redimensionamiento a 720 por 480, grises, ROI, lesión y descriptor Gabor. | 19 |
| [app/src/main/java/com/example/evogmd/imagen/PanelImagen.java](app/src/main/java/com/example/evogmd/imagen/PanelImagen.java) | Componente ImageView compatible con usos anteriores del proyecto. | 5 |
| [app/src/main/java/com/example/evogmd/imagen/MarcoImagen.java](app/src/main/java/com/example/evogmd/imagen/MarcoImagen.java) | Añade paneles XML reutilizables para visualizar resultados intermedios. | 2 |
| [app/src/main/java/com/example/evogmd/leerimagen/CargarImagen.java](app/src/main/java/com/example/evogmd/leerimagen/CargarImagen.java) | Centraliza los intents de selección y la lectura de imágenes mediante URI. | 6 |
| [app/src/main/java/com/example/evogmd/leerimagen/CameraPhoto.java](app/src/main/java/com/example/evogmd/leerimagen/CameraPhoto.java) | Decodifica fotos y aplica orientación EXIF sin controlar la exposición de la cámara. | 2 |
| [app/src/test/java/com/example/evogmd/dataset/DivisionTrainTestTest.java](app/src/test/java/com/example/evogmd/dataset/DivisionTrainTestTest.java) | Pruebas unitarias de DivisionTrain. | 2 |
| [app/src/test/java/com/example/evogmd/descriptor/MSGFBTest.java](app/src/test/java/com/example/evogmd/descriptor/MSGFBTest.java) | Pruebas unitarias de MSGFB. | 1 |
| [app/src/test/java/com/example/evogmd/transformaciones/RecortarROICameraTest.java](app/src/test/java/com/example/evogmd/transformaciones/RecortarROICameraTest.java) | Pruebas unitarias de RecortarROICamera. | 1 |
| [app/src/test/java/com/example/evogmd/transformaciones/BinarizacionTest.java](app/src/test/java/com/example/evogmd/transformaciones/BinarizacionTest.java) | Pruebas unitarias de Binarizacion. | 1 |
| [app/src/test/java/com/example/evogmd/transformaciones/ParametrosGaborTest.java](app/src/test/java/com/example/evogmd/transformaciones/ParametrosGaborTest.java) | Pruebas unitarias de ParametrosGabor. | 2 |

## Índice XML

| Archivo | Propósito |
|---|---|
| [app/src/main/AndroidManifest.xml](app/src/main/AndroidManifest.xml) | Configuración Android: tema, actividades, entrada de login y FileProvider para capturas. La aplicación de cámara es externa y recibe permisos temporales sobre la URI. |
| [app/src/main/res/values/ui_arrays.xml](app/src/main/res/values/ui_arrays.xml) | Opciones del selector de rol: índice 0 corresponde a USER e índice 1 a ADMIN. El controlador usa ese orden al crear cuentas. |
| [app/src/main/res/values/colors.xml](app/src/main/res/values/colors.xml) | Paleta de recursos con nombre. Algunos layouts también conservan colores declarados directamente; editar este archivo no sustituye esos valores. |
| [app/src/main/res/values/themes.xml](app/src/main/res/values/themes.xml) | Recurso XML themes utilizado por la configuración Android del proyecto. |
| [app/src/main/res/values/strings.xml](app/src/main/res/values/strings.xml) | Textos de recursos del proyecto. Incluye cadenas históricas de entrenamiento que no se muestran en las pantallas actuales. |
| [app/src/main/res/values/ui_styles.xml](app/src/main/res/values/ui_styles.xml) | Estilos compartidos de tarjetas, botones y campos. Centralizan dimensiones, márgenes, colores y fondos utilizados por los layouts. |
| [app/src/main/res/drawable/bg_card.xml](app/src/main/res/drawable/bg_card.xml) | Fondo de tipo shape para card. Define el color de relleno y el radio de las esquinas; los layouts o estilos lo referencian como drawable. |
| [app/src/main/res/drawable/bg_input.xml](app/src/main/res/drawable/bg_input.xml) | Fondo de tipo shape para input. Define el color de relleno y el radio de las esquinas; los layouts o estilos lo referencian como drawable. |
| [app/src/main/res/drawable/panel_border.xml](app/src/main/res/drawable/panel_border.xml) | Recurso XML panel_border utilizado por la configuración Android del proyecto. |
| [app/src/main/res/drawable/bg_hero.xml](app/src/main/res/drawable/bg_hero.xml) | Fondo de tipo shape para hero. Define el color de relleno y el radio de las esquinas; los layouts o estilos lo referencian como drawable. |
| [app/src/main/res/drawable/bg_preview.xml](app/src/main/res/drawable/bg_preview.xml) | Fondo de tipo shape para preview. Define el color de relleno y el radio de las esquinas; los layouts o estilos lo referencian como drawable. |
| [app/src/main/res/drawable/bg_primary.xml](app/src/main/res/drawable/bg_primary.xml) | Fondo de tipo shape para primary. Define el color de relleno y el radio de las esquinas; los layouts o estilos lo referencian como drawable. |
| [app/src/main/res/drawable/bg_button.xml](app/src/main/res/drawable/bg_button.xml) | Fondo de tipo shape para button. Define el color de relleno y el radio de las esquinas; los layouts o estilos lo referencian como drawable. |
| [app/src/main/res/xml/camera_paths.xml](app/src/main/res/xml/camera_paths.xml) | Rutas limitadas que FileProvider puede compartir con la aplicación de cámara. La captura se guarda en el subdirectorio camera de caché. |
| [app/src/main/res/layout/activity_loading.xml](app/src/main/res/layout/activity_loading.xml) | Pantalla temporal mostrada mientras se abre o crea la base de datos de autenticación. |
| [app/src/main/res/layout/activity_login.xml](app/src/main/res/layout/activity_login.xml) | Pantalla de inicio de sesión. LoginActivity enlaza email, password, message, submit y switch_screen; la comprobación de credenciales se ejecuta en Java. |
| [app/src/main/res/layout/item_image_panel.xml](app/src/main/res/layout/item_image_panel.xml) | Panel reutilizable para imagen gris, máscara ROI, lesión y respuestas Gabor. Java asigna el título y el bitmap. |
| [app/src/main/res/layout/dialog_text.xml](app/src/main/res/layout/dialog_text.xml) | Contenido reutilizable para reportes y consultas. AnalysisActivity asigna el texto dinámico a dialog_content. |
| [app/src/main/res/layout/activity_user.xml](app/src/main/res/layout/activity_user.xml) | Pantalla del usuario. Permite seleccionar o capturar una imagen y consultar la clasificación; no incluye controles administrativos. |
| [app/src/main/res/layout/activity_admin.xml](app/src/main/res/layout/activity_admin.xml) | Pantalla de administrador. AnalysisActivity enlaza los controles tras comprobar la sesión y permite modelos, cuentas y detalles del procesamiento. |
| [app/src/main/res/layout/processing_placeholder.xml](app/src/main/res/layout/processing_placeholder.xml) | Contenido inicial del panel administrativo antes de analizar una imagen. |
| [app/src/main/res/layout/item_heading.xml](app/src/main/res/layout/item_heading.xml) | Encabezado reutilizable que se infla dentro del panel de detalles técnicos. |
| [app/src/main/res/layout/activity_register.xml](app/src/main/res/layout/activity_register.xml) | Registro público de usuarios. LoginActivity valida nombre, correo y confirmación. El rol asignado es USER; este formulario no concede administración. |
| [app/src/main/res/layout/item_processing_info.xml](app/src/main/res/layout/item_processing_info.xml) | Texto técnico seleccionable con origen de ROI, modelo, vector de características y distribución del clasificador. |
| [app/src/main/res/layout/dialog_create_account.xml](app/src/main/res/layout/dialog_create_account.xml) | Formulario de alta abierto por el administrador. El rol se elige desde account_roles y DatabaseHelper vuelve a comprobar los permisos al guardar. |

## Comprobación de esta entrega

Se comparan los tokens Java y las estructuras XML con la versión anterior para comprobar que los cambios son de documentación. Se compilan los recursos con AAPT2 y las fuentes principales con ECJ usando Android API 35. Estas comprobaciones no sustituyen una prueba en teléfono ni validan la exactitud clínica del clasificador.

## Reportes PDF y persistencia local

- `report/ResultReport.java`: copia privada del resultado, creación y validación del PDF, recuperación por UUID y pendientes de SQLite.
- `report/PdfPreview.java`: visor integrado de PDF con navegación entre páginas.
- `storage/ModelDatabase.java`: migración aditiva y guardado transaccional de resultados junto al catálogo.
- `dialog_pdf_preview.xml`: componentes del visor.
- `AnalysisActivity.java`: clasificación, guardado local y exportación con recuperación del identificador después de una recreación de Android.

No existe dependencia de API remota en esta versión.
