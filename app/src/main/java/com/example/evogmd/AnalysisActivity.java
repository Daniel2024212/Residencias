/*
 * DOCUMENTACIÓN DEL ARCHIVO
 * Controlador compartido de las pantallas de administrador y usuario. Coordina XML, sesión, captura, trabajo en segundo plano y presentación de predicciones.
 */
package com.example.evogmd;

import android.app.Activity;
import com.example.evogmd.report.ResultReport;
import com.example.evogmd.report.PdfPreview;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.ClipData;
import android.provider.MediaStore;
import androidx.core.content.FileProvider;
import java.io.File;
import com.example.evogmd.leerimagen.CameraPhoto;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Build;
import android.Manifest;
import android.content.pm.PackageManager;
import android.view.View;
import android.widget.*;
import com.example.evogmd.clasificacion.ClasificadorSVM;
import com.example.evogmd.imagen.Imagen;
import com.example.evogmd.leerimagen.CargarImagen;
import com.example.evogmd.login.DatabaseHelper;
import com.example.evogmd.login.LoginActivity;
import com.example.evogmd.login.Session;
import com.example.evogmd.storage.ModelDatabase;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Pantallas por rol. El pipeline Imagen -> Gabor -> SVM permanece intacto. */
public abstract class AnalysisActivity extends Activity {
    /**
     * Indica el rol de la pantalla para elegir el layout y comprobar la autorización.
     * 
     * @return true cuando se cumple la condición descrita; false en caso contrario.
     */
    protected abstract boolean adminScreen();
    private boolean externalActivity, leaving;
    private final android.os.Handler idleHandler=new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable idleCheck=new Runnable(){/**
     * Ejecuta la comprobación periódica de sesión y programa la siguiente cuando sigue activa.
     */
    public void run(){
        if(!Session.isActive(AnalysisActivity.this)){Session.logout(AnalysisActivity.this);returnToLogin();}
        else idleHandler.postDelayed(this,1000);
    }};
    /**
     * Registra la interacción del usuario para actualizar el temporizador de sesión.
     */
    @Override public void onUserInteraction(){super.onUserInteraction();Session.touch(AnalysisActivity.this);}
    /**
     * Marca la apertura de una actividad externa para distinguirla de una salida voluntaria.
     * 
     * @param intent solicitud enviada a la actividad externa.
     * @param request código que identifica la actividad externa.
     */
    @Override public void startActivityForResult(Intent intent,int request){
        externalActivity=true;
        try{super.startActivityForResult(intent,request);}catch(RuntimeException e){externalActivity=false;throw e;}
    }
    /**
     * Detiene la vigilancia de inactividad y cierra sesión al abandonar la app, excepto durante una actividad externa iniciada por ella o un cambio de configuración.
     */
    @Override protected void onStop(){
        idleHandler.removeCallbacks(idleCheck);
        if(!externalActivity&&!isChangingConfigurations())Session.logout(this);
        super.onStop();
    }
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private DatabaseHelper.User current;
    private LinearLayout details;
    private TextView status, result, modelInfo;
    private ImageView preview;
    private ProgressBar progress;
    private Button camera, select, predict, importButton, reportButton, usersButton, datasetButton;
    private Uri selected, pendingCamera;
    private File pendingCameraFile;
    private ResultReport latestReport;
    private String exportingReportId;
    private Button savePdf, openPdf;
    private TextView storageStatus;
    private boolean savingResults;
    private static final int PDF_EXPORT=43;
    private static final String PDF_PUBLIC_FOLDER="Documentos/EvoGMD/ResultadosPDF";
    private boolean selectedPhoto, autoAnalyze, initializing=true;
    private boolean busy=true, ready=false;
    private static final int IMAGE=40, MODEL=41, CAMERA=42;
    private static final int INK=0xFF172D39, MUTED=0xFF607380, TEAL=0xFF087F8C;

    /**
     * Inicializa la actividad, restaura el estado necesario y prepara sus datos y controles.
     * 
     * @param state estado guardado por Android, o null en una apertura nueva.
     */
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);

        // Si Android destruyó el proceso mientras la app de cámara estaba abierta,
        // la variable estática de sesión ya no existe. El estado guardado sí nos
        // permite reconocer que Evo-GMD fue quien abrió la cámara y reanudar la
        // sesión almacenada sin tratarlo como una salida voluntaria de la app.
        externalActivity=state!=null&&state.getBoolean("external_activity");
        String restoredPendingCamera=state==null?null:state.getString("camera_pending");
        if(externalActivity && restoredPendingCamera!=null) Session.touch(this);

        if(!Session.isActive(this)){Session.logout(this);returnToLogin();return;}
        if(Build.VERSION.SDK_INT<29 && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)!=PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},72);
        if(state!=null && state.getString("imagen")!=null) selected=Uri.parse(state.getString("imagen"));
        if(state!=null) {
            if(restoredPendingCamera!=null) pendingCamera=Uri.parse(restoredPendingCamera);
            selectedPhoto=state.getBoolean("camera_photo");
            autoAnalyze=state.getBoolean("camera_auto");
        }
        if(state!=null)exportingReportId=state.getString("exporting_report_id");
        buildUi();
        worker.execute(() -> {
            try {
                DatabaseHelper.User user=Session.current(this);
                if(!authorized(user)) { ui(this::returnToLogin);return; }
                current=user;
                boolean loaded=ClasificadorSVM.cargarModeloGuardado(this);
                String info;
                try(ModelDatabase db=new ModelDatabase(this)) { info=db.summary(); }
                final String summary=info;
                ui(() -> {
                    initializing=false;ready=loaded;
                    if(adminScreen()) modelInfo.setText(summary);
                    setBusy(false,loaded ? "Modelo listo. Selecciona una imagen o toma una foto." : "No se pudo cargar el modelo. Contacta al administrador.");
                    if(!loaded && adminScreen()) result.setText(ClasificadorSVM.getUltimoErrorCarga());
                    if(selected!=null) loadPreview(selected);
                    savePendingResults();
                });
            } catch(Exception error) { ui(() -> fail(error)); }
        });
    }
    /**
     * Reanuda la comprobación de sesión y verifica que el usuario conserve el rol autorizado.
     */
    @Override protected void onResume() {
        super.onResume();
        if(leaving)return;
        if(!Session.isActive(this)){Session.logout(this);returnToLogin();return;}
        idleHandler.removeCallbacks(idleCheck);idleHandler.postDelayed(idleCheck,1000);
        worker.execute(() -> {
            try {
                DatabaseHelper.User user=Session.current(this);
                if(!authorized(user)) ui(this::returnToLogin);
            } catch(Exception e) { ui(() -> fail(e)); }
        });
    }
    /**
     * Comprueba que existe una cuenta y que su rol coincide con el de esta pantalla.
     * 
     * @param user cuenta autenticada que se consulta.
     * @return true cuando se cumple la condición descrita; false en caso contrario.
     */
    private boolean authorized(DatabaseHelper.User user) {
        return user!=null && (adminScreen() ? user.isAdmin() : !user.isAdmin());
    }
    /**
     * Abre el login limpiando la pila de actividades para impedir volver a una pantalla protegida.
     */
    private void returnToLogin() {
        if(leaving)return;leaving=true;idleHandler.removeCallbacks(idleCheck);
        Intent i=new Intent(this,LoginActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);finish();
    }
    /**
     * Publica una actualización en el hilo principal solo si la actividad todavía puede mostrarla.
     * 
     * @param r trabajo que se ejecutará en el hilo principal.
     */
    private void ui(Runnable r) { runOnUiThread(() -> { if(!isDestroyed() && !isFinishing() && !leaving) r.run(); }); }
    /**
     * Infla un encabezado XML reutilizable y lo añade al contenedor indicado.
     * 
     * @param box contenedor visual que recibirá el encabezado.
     * @param title título visible.
     */
    private void heading(LinearLayout box,String title) {
        TextView heading=(TextView)getLayoutInflater().inflate(R.layout.item_heading,box,false);
        heading.setText(title); box.addView(heading);
    }
    /**
     * Infla la pantalla XML según el rol, enlaza sus controles y registra sus eventos.
     */
    private void buildUi() {
        setContentView(adminScreen()?R.layout.activity_admin:R.layout.activity_user);
        View root=findViewById(android.R.id.content);
        root.setOnApplyWindowInsetsListener((view,insets) -> {
            view.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());
            return insets.consumeSystemWindowInsets();
        });
        root.requestApplyInsets();
        preview=findViewById(R.id.preview); progress=findViewById(R.id.progress);
        status=findViewById(R.id.status); result=findViewById(R.id.result);
        select=findViewById(R.id.select_image); camera=findViewById(R.id.camera); predict=findViewById(R.id.predict);
        findViewById(R.id.logout).setOnClickListener(v -> {Session.logout(this);returnToLogin();});
        select.setOnClickListener(v -> startActivityForResult(CargarImagen.crearIntentSeleccionImagen(),IMAGE));
        camera.setOnClickListener(v -> takePhoto()); predict.setOnClickListener(v -> analyze());
        if(adminScreen()) {
            details=findViewById(R.id.details); modelInfo=findViewById(R.id.model_info);
            reportButton=findViewById(R.id.report); importButton=findViewById(R.id.import_model);
            usersButton=findViewById(R.id.users); datasetButton=findViewById(R.id.dataset);
            reportButton.setOnClickListener(v -> showReport());
            importButton.setOnClickListener(v -> new Session.DialogBuilder(this).setTitle("Cambiar modelo activo")
                .setMessage("Selecciona un modelo de confianza exportado por Evo-GMD. Se validará antes de activarlo y no se reentrenará.")
                .setPositiveButton("Seleccionar",(d,w) -> {
                    Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT);intent.setType("*/*");intent.addCategory(Intent.CATEGORY_OPENABLE);
                    startActivityForResult(intent,MODEL);
                }).setNegativeButton("Cancelar",null).show());
            usersButton.setOnClickListener(v -> showUsers()); datasetButton.setOnClickListener(v -> showDataset());
        }
        savePdf=findViewById(R.id.save_pdf);storageStatus=findViewById(R.id.storage_status);
        savePdf.setOnClickListener(v -> exportPdf());
        openPdf=findViewById(R.id.open_pdf);openPdf.setOnClickListener(v -> {if(latestReport!=null)PdfPreview.show(this,latestReport.pdf());});
        updateButtons();
    }
    /**
     * Actualiza el estado de trabajo y habilita o bloquea los controles correspondientes.
     * 
     * @param value valor recibido para actualizar el estado o normalizar el texto, según la firma.
     * @param message mensaje de estado para el usuario.
     */
    private void setBusy(boolean value,String message) { busy=value;status.setText(message);progress.setVisibility(value?View.VISIBLE:View.GONE);updateButtons(); }
    /**
     * Habilita las acciones según la carga del modelo, la selección actual y el trabajo en curso.
     */
    private void updateButtons() {
        if(savePdf!=null){savePdf.setEnabled(!busy&&latestReport!=null);openPdf.setEnabled(!busy&&latestReport!=null);}
        select.setEnabled(!busy);camera.setEnabled(!busy&&ready);predict.setEnabled(!busy&&ready&&selected!=null);
        if(adminScreen()) {
            importButton.setEnabled(!busy);reportButton.setEnabled(!busy&&ready);usersButton.setEnabled(!busy);datasetButton.setEnabled(!busy);
        }
    }
    /**
     * Procesa el resultado de cámara, selector de imagen o importación de modelo; una captura confirmada inicia el análisis automáticamente.
     * 
     * @param request código que identifica la actividad externa.
     * @param code código de resultado devuelto por Android.
     * @param data intent con el resultado; puede ser null para la cámara.
     */
    @Override protected void onActivityResult(int request,int code,Intent data) {
        super.onActivityResult(request,code,data);
        externalActivity=false;

        // Regresar de la cámara es una continuación de la misma operación del
        // usuario. Refrescamos primero la sesión para que una captura larga o una
        // recreación del proceso no mande al LoginActivity.
        if(request==CAMERA) Session.touch(this);
        if(!Session.isActive(this)){Session.logout(this);returnToLogin();return;}
        Session.touch(this);
        if(request==PDF_EXPORT){
            final String reportId=exportingReportId;exportingReportId=null;
            if(code==RESULT_OK&&data!=null&&data.getData()!=null){
                final Uri target=data.getData();worker.execute(() -> {
                    try{
                        DatabaseHelper.User owner=Session.current(this);if(owner==null)throw new SecurityException("Sesión terminada.");
                        ResultReport report=ResultReport.load(this,owner.id,reportId);report.validatePdf();
                        // Cerrar y vaciar el destino antes de anunciar que se guardó.
                        try(InputStream in=new java.io.FileInputStream(report.pdf());java.io.OutputStream out=getContentResolver().openOutputStream(target,"wt")){
                            if(out==null)throw new java.io.IOException("No se pudo abrir la carpeta elegida.");ResultReport.copy(in,out);out.flush();
                        }
                        ui(() -> Toast.makeText(this,"PDF guardado correctamente",Toast.LENGTH_LONG).show());
                    }catch(Exception e){
                        // No dejar un documento vacío que después el visor no pueda abrir.
                        try{getContentResolver().delete(target,null,null);}catch(Exception ignored){}
                        ui(() -> dialog("No se pudo guardar el PDF", "Usa Abrir PDF para ver el reporte o vuelve a guardarlo en Descargas. "+e.getMessage()));
                    }
                });
            }return;
        }
        if(request==CAMERA) {
            Uri photo=pendingCamera;pendingCamera=null;pendingCameraFile=null;
            if(photo!=null) revokeUriPermission(photo,Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            if(code==RESULT_OK && photo!=null) {
                selected=photo;selectedPhoto=true;autoAnalyze=true;
                if(!initializing) loadPreview(photo);
            } else {
                if(photo!=null) getContentResolver().delete(photo,null,null);
                if(!initializing) setBusy(false,"Captura cancelada. Puedes volver a intentarlo.");
            }
            return;
        }
        if(code!=RESULT_OK || data==null || data.getData()==null) return;
        Uri uri=data.getData();
        try { getContentResolver().takePersistableUriPermission(uri,data.getFlags()&Intent.FLAG_GRANT_READ_URI_PERMISSION); } catch(SecurityException ignored) { }
        if(request==IMAGE) { selected=uri;selectedPhoto=false;autoAnalyze=false;loadPreview(uri); }
        if(request==MODEL && adminScreen()) importModel(uri);
    }
    /**
     * Prepara un archivo temporal y abre la cámara del teléfono con permiso temporal de escritura mediante FileProvider. No configura brillo ni exposición.
     */
    private void takePhoto() {
        if(busy || !ready) return;
        try {
            File folder=new File(getCacheDir(),"camera");
            if(!folder.isDirectory() && !folder.mkdirs()) throw new java.io.IOException("No se pudo preparar la cámara.");
            File file=File.createTempFile("captura_", ".jpg",folder);
            pendingCamera=FileProvider.getUriForFile(this,getPackageName()+".camera.files",file);
            pendingCameraFile=file;
            Intent intent=new Intent(this,EncuadreCameraActivity.class);
            intent.putExtra("output_path",file.getAbsolutePath());
            // Abrir la cámara no cuenta como abandonar Evo-GMD. Persistimos la
            // actividad justo antes de entregar el control a la app de cámara.
            Session.touch(this);
            startActivityForResult(intent,CAMERA);
        } catch(Exception e) {
            if(pendingCamera!=null) { getContentResolver().delete(pendingCamera,null,null);pendingCamera=null; }
            new Session.DialogBuilder(this).setTitle("Cámara no disponible")
                .setMessage("No se pudo abrir la cámara. Puedes seleccionar una imagen guardada.")
                .setPositiveButton("Aceptar",null).show();
        }
    }
    /**
     * Decodifica la imagen en el ejecutor y publica su vista previa; descarta resultados de una selección anterior.
     * 
     * @param uri URI del documento que se consulta.
     */
    private void loadPreview(Uri uri) {
        latestReport=null;
        final boolean cameraImage=selectedPhoto;
        setBusy(true,"Abriendo imagen…");result.setText("Tu resultado aparecerá aquí.");
        if(details!=null) details.removeAllViews();
        worker.execute(() -> {
            try {
                Bitmap bitmap=cameraImage?CameraPhoto.open(this,uri):CargarImagen.abrirImagen(this,uri);
                ui(() -> { if(!uri.equals(selected)){bitmap.recycle();return;} preview.setImageBitmap(bitmap);setBusy(false,"Imagen lista para analizar.");if(autoAnalyze&&ready){autoAnalyze=false;analyze();} });
            } catch(Exception e) { selected=null;ui(() -> fail(e)); }
        });
    }
    /**
     * Recupera la máscara por hash cuando existe, calcula el descriptor y elige el SVM general o el específico de cámara.
     */
    private void analyze() {
        if(busy || !ready || selected==null) return;
        final Uri imageUri=selected;setBusy(true,"Analizando imagen…");result.setText("Procesando…");
        worker.execute(() -> {
            Bitmap original=null,mask=null;
            try {
                DatabaseHelper.User user=Session.current(this);
                if(!authorized(user)) { ui(this::returnToLogin);return; }
                String hash;
                try(InputStream in=getContentResolver().openInputStream(imageUri)) {
                    if(in==null) throw new IllegalArgumentException("No se pudo abrir la imagen");hash=ModelDatabase.hashStream(in);
                }
                try(ModelDatabase db=new ModelDatabase(this)) { mask=db.maskForHash(hash); }
                boolean expert=mask!=null;
                original=selectedPhoto?CameraPhoto.open(this,imageUri):CargarImagen.abrirImagen(this,imageUri);
                Imagen processed=new Imagen(original,mask,true,selectedPhoto);
                double[] vector=processed.getVectorCaracteristicas();

                // Una foto nueva de cámara usa ROI automática. Ese descriptor no
                // debe entrar al SVM entrenado con máscaras expertas porque ambas
                // distribuciones son diferentes y el modelo se sesgaba a Nevus.
                final boolean cameraAutoModel = selectedPhoto && !expert;
                String predicted = cameraAutoModel
                        ? ClasificadorSVM.predecirImagenCamara(vector)
                        : ClasificadorSVM.predecirImagen(vector);
                double[] dist = cameraAutoModel
                        ? ClasificadorSVM.obtenerDistribucionCamara(vector)
                        : ClasificadorSVM.obtenerDistribucion(vector);
                final boolean expertMask=expert;
                ResultReport generated=null;String reportError=null;
                try{generated=ResultReport.create(this,user.id,imageUri,original,CargarImagen.obtenerNombre(this,imageUri),"Melanoma".equals(predicted)?"Alta probabilidad":"Baja probabilidad");
                    if(!adminScreen())generated.saveUserPdf(this,user.id);}
                catch(Exception e){reportError="Clasificación lista, pero no se pudo crear el PDF ni conservar el registro. Reintenta el análisis.";}
                final Bitmap outlined=selectedPhoto?RoiContour.draw(processed):null;
                final ResultReport report=generated;final String error=reportError;
                ui(() -> {latestReport=report;display(processed,predicted,dist,expertMask,cameraAutoModel);
                    if(outlined!=null)preview.setImageBitmap(outlined);
                    if(error!=null)storageStatus.setText(error);else{storageStatus.setText(adminScreen()?"Reporte listo; puedes guardarlo en "+PDF_PUBLIC_FOLDER:"PDF guardado automáticamente en "+PDF_PUBLIC_FOLDER);savePendingResults();}});
            } catch(Exception e) { ui(() -> fail(e)); }
            finally { if(original!=null) original.recycle();if(mask!=null) mask.recycle(); }
        });
    }
    /**
     * Traduce la etiqueta del modelo a Baja probabilidad o Alta probabilidad y muestra los detalles técnicos solo al administrador.
     * 
     * @param image resultado del procesamiento que se mostrará.
     * @param label etiqueta nominal devuelta por Weka.
     * @param dist distribución del clasificador en orden Nevus, Melanoma.
     * @param expert indica si se recuperó una máscara experta.
     * @param cameraAutoModel indica si se empleó el SVM específico de cámara.
     */
    private void display(Imagen image,String label,double[] dist,boolean expert,boolean cameraAutoModel) {
        boolean malignant="Melanoma".equals(label);
        result.setText(malignant?"Alta probabilidad":"Baja probabilidad");result.setTextColor(malignant?0xFFAF3856:TEAL);
        if(adminScreen()) {
            details.removeAllViews();heading(details,"03  Detalle del procesamiento");
            TextView info=(TextView)getLayoutInflater().inflate(R.layout.item_processing_info,details,false);
            info.setText("ROI: "+(expert?"máscara experta recuperada de SQLite":(selectedPhoto?"segmentación automática de la foto":"segmentación automática original"))
                    +"\nModelo: "+(cameraAutoModel?"SVM BEST Java8 V1 (cámara)":"SVM BEST Java8 V1")
                    +"\nClase Weka: "+label+"\n\n"+image.construirReporteVector()
                    +String.format(Locale.US,"\nDistribución del clasificador\nNevus: %.6f\nMelanoma: %.6f",dist[0],dist[1]));
            details.addView(info);
            imagePanel("Escala de grises",image.getBitmapGris());imagePanel("Máscara ROI",image.getBitmapMascara());
            imagePanel("Lesión recortada",image.getBitmapLesion());
            for(int i=0;i<4;i++) imagePanel("Filtro Gabor BEST "+(i+1),image.getBitmapGabor(i));
        }
        setBusy(false,"Análisis terminado.");
    }
    /** Admin: exporta el PDF opcionalmente a la misma carpeta pública de Documentos. */
    private void exportPdf(){
        if(latestReport==null || !adminScreen()) return;
        final ResultReport report=latestReport;
        worker.execute(() -> {
            try {
                DatabaseHelper.User owner=Session.current(this);
                if(owner==null) throw new SecurityException("Sesión terminada");
                report.validatePdf();
                Uri saved=report.saveUserPdf(this,owner.id);
                ui(() -> {storageStatus.setText("PDF guardado en "+PDF_PUBLIC_FOLDER);
                    Toast.makeText(this,"PDF guardado en Documentos/EvoGMD/ResultadosPDF",Toast.LENGTH_LONG).show();});
            } catch(Exception e) {ui(() -> dialog("Error al guardar PDF",e.getMessage()));}
        });
    }
    /** Guarda pendientes del propietario autenticado, sin Internet ni servidor. Conserva el PDF si SQLite falla. */
    private void savePendingResults(){
        if(savingResults)return;savingResults=true;updateButtons();storageStatus.setText("Guardando en SQLite…");
        worker.execute(() -> {
            String message;
            try{
                DatabaseHelper.User owner=Session.current(this);if(owner==null)throw new SecurityException("Inicia sesión de nuevo.");
                List<ResultReport> reports=ResultReport.pending(this,owner.id);int count=0;
                try(ModelDatabase db=new ModelDatabase(this)){
                    for(ResultReport report:reports){db.saveResult(this,owner.id,report);report.markStored();count++;}
                }
                message=count==0?"SQLite local lista. No hay guardados pendientes.":"Guardado en SQLite: "+count+" resultado(s), con imagen y nombre de archivo.";
            }catch(Exception e){message="Guardado pendiente: "+(e.getMessage()==null?"revisa el espacio disponible y reintenta.":e.getMessage());}
            final String text=message;ui(() -> {savingResults=false;storageStatus.setText(text);updateButtons();});
        });
    }
    /**
     * Infla el panel XML de imagen y asigna el título, bitmap y descripción accesible.
     * 
     * @param title título visible.
     * @param bitmap imagen que se muestra o procesa.
     */
    private void imagePanel(String title,Bitmap bitmap) {
        View panel=getLayoutInflater().inflate(R.layout.item_image_panel,details,false);
        ((TextView)panel.findViewById(R.id.image_title)).setText(title);
        ImageView image=panel.findViewById(R.id.panel_image);
        image.setImageBitmap(bitmap);image.setContentDescription(title);details.addView(panel);
    }
    /**
     * Consulta el reporte almacenado con el modelo después de verificar el acceso de administrador.
     */
    private void showReport() {
        worker.execute(() -> {
            try { Session.requireAdmin(this);String report=ClasificadorSVM.getReporteEvaluacion();ui(() -> dialog("Reporte guardado con el modelo",report==null?"Sin reporte":report)); }
            catch(Exception e) { ui(() -> fail(e)); }
        });
    }
    /**
     * Muestra texto seleccionable dentro del layout XML reutilizable de diálogo.
     * 
     * @param title título visible.
     * @param content texto que se mostrará.
     */
    private void dialog(String title,String content) {
        View contentView=getLayoutInflater().inflate(R.layout.dialog_text,null);
        ((TextView)contentView.findViewById(R.id.dialog_content)).setText(content);
        new Session.DialogBuilder(this).setTitle(title).setView(contentView).setPositiveButton("Cerrar",null).show();
    }
    /**
     * Valida e importa un modelo entrenado desde una URI y actualiza la información visible.
     * 
     * @param uri URI del documento que se consulta.
     */
    private void importModel(Uri uri) {
        setBusy(true,"Validando modelo entrenado…");
        worker.execute(() -> {
            try(InputStream in=getContentResolver().openInputStream(uri)) {
                Session.requireAdmin(this);
                ClasificadorSVM.importarModeloSql(this,ModelDatabase.readModel(in),CargarImagen.obtenerNombre(this,uri));
                String info;try(ModelDatabase db=new ModelDatabase(this)) { info=db.summary(); }
                ui(() -> { ready=true;modelInfo.setText(info);result.setText("Modelo actualizado. Vuelve a analizar la imagen.");if(details!=null) details.removeAllViews();setBusy(false,"Modelo activado sin reentrenar."); });
            } catch(Exception e) { ui(() -> fail(e)); }
        });
    }
    /**
     * Consulta las cuentas y permite abrir el alta o conceder permisos de administrador.
     */
    private void showUsers() {
        worker.execute(() -> {
            try(DatabaseHelper db=new DatabaseHelper(this)) {
                Session.requireAdmin(this);DatabaseHelper.User actor=Session.current(this);
                List<DatabaseHelper.User> users=db.listUsers(actor.id);String[] labels=new String[users.size()];
                for(int i=0;i<users.size();i++) labels[i]=users.get(i).name+" · "+users.get(i).role+"\n"+users.get(i).email;
                ui(() -> new Session.DialogBuilder(this).setTitle("Cuentas del dispositivo").setItems(labels,(d,index) -> {
                    DatabaseHelper.User user=users.get(index);if(user.isAdmin()) return;
                    new Session.DialogBuilder(this).setTitle("Dar acceso de administrador")
                        .setMessage("¿Permitir a "+user.email+" administrar cuentas y modelos?")
                        .setPositiveButton("Dar acceso",(dialog,which) -> worker.execute(() -> {
                            try(DatabaseHelper auth=new DatabaseHelper(this)) {
                                Session.requireAdmin(this);auth.promote(Session.current(this).id,user.id);ui(this::showUsers);
                            } catch(Exception e) { ui(() -> fail(e)); }
                        })).setNegativeButton("Cancelar",null).show();
                }).setPositiveButton("Crear cuenta",(d,w) -> createAccountDialog()).setNegativeButton("Cerrar",null).show());
            } catch(Exception e) { ui(() -> fail(e)); }
        });
    }
    /**
     * Infla el formulario XML, valida los campos y solicita a SQLite crear una cuenta con el rol elegido por un administrador.
     */
    private void createAccountDialog() {
        if(!adminScreen()) return;
        View scroll=getLayoutInflater().inflate(R.layout.dialog_create_account,null);
        EditText name=scroll.findViewById(R.id.name), email=scroll.findViewById(R.id.email);
        EditText password=scroll.findViewById(R.id.password), confirmation=scroll.findViewById(R.id.confirmation);
        Spinner role=scroll.findViewById(R.id.role);
        TextView error=scroll.findViewById(R.id.account_error);
        AlertDialog dialog=new Session.DialogBuilder(this).setTitle("Crear cuenta").setView(scroll)
            .setPositiveButton("Crear",null).setNegativeButton("Cancelar",null).create();
        dialog.setOnShowListener(v -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(button -> {
            String n=name.getText().toString().trim(),address=email.getText().toString().trim();
            if(n.isEmpty()){error.setText("Ingresa el nombre.");return;}
            if(!android.util.Patterns.EMAIL_ADDRESS.matcher(address).matches()){error.setText("Correo inválido.");return;}
            if(password.length()<8){error.setText("Usa al menos 8 caracteres.");return;}
            if(!password.getText().toString().equals(confirmation.getText().toString())){error.setText("Las contraseñas no coinciden.");return;}
            char[] secret=password.getText().toString().toCharArray();
            String selectedRole=role.getSelectedItemPosition()==1?"ADMIN":"USER";
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setEnabled(false);dialog.setCancelable(false);error.setText("Creando cuenta…");
            worker.execute(() -> {
                try(DatabaseHelper db=new DatabaseHelper(this)) {
                    Session.requireAdmin(this);DatabaseHelper.User actor=Session.current(this);
                    db.createAccount(actor.id,n,address,secret,selectedRole);
                    ui(() -> {password.setText("");confirmation.setText("");dialog.dismiss();Toast.makeText(this,"Cuenta creada",Toast.LENGTH_SHORT).show();showUsers();});
                } catch(Exception e) {
                    ui(() -> {
                        error.setText(e instanceof android.database.sqlite.SQLiteConstraintException ? "Ese correo ya está registrado." : e.getMessage());
                        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setEnabled(true);dialog.setCancelable(true);
                    });
                } finally {java.util.Arrays.fill(secret,'\0');}
            });
        }));
        dialog.show();
    }
    /**
     * Consulta los nombres y clases del catálogo SQLite para mostrarlos en un diálogo.
     */
    private void showDataset() {
        worker.execute(() -> {
            try(ModelDatabase db=new ModelDatabase(this)) {
                List<String> names=db.imageNames(this);
                ui(() -> dialog("Imágenes almacenadas en SQLite",android.text.TextUtils.join("\n",names)));
            } catch(Exception e) { ui(() -> fail(e)); }
        });
    }
    /**
     * Finaliza el estado de espera y muestra el error con el nivel de detalle permitido por el rol.
     * 
     * @param e excepción que se mostrará según el rol.
     */
    private void fail(Exception e) {
        setBusy(false,"No se pudo completar la operación.");
        if("Procesando…".contentEquals(result.getText())) result.setText("Resultado no disponible");
        if(adminScreen()) dialog("Detalle",e.getMessage()==null?e.toString():e.getMessage());
        else new Session.DialogBuilder(this).setTitle("No se pudo analizar").setMessage(selectedPhoto ? "No se pudo delimitar la zona. Acerca la cámara, centra la lesión, evita sombras y reflejos y repite la toma." : "Prueba otra imagen o consulta al administrador.").setPositiveButton("Aceptar",null).show();
    }
    /**
     * Guarda las URI y banderas necesarias para restaurar la operación después de una recreación.
     * 
     * @param state estado guardado por Android, o null en una apertura nueva.
     */
    @Override protected void onSaveInstanceState(Bundle state) {
        state.putString("exporting_report_id",exportingReportId);
        state.putBoolean("external_activity",externalActivity);
        if(selected!=null) state.putString("imagen",selected.toString());
        if(pendingCamera!=null) state.putString("camera_pending",pendingCamera.toString());
        state.putBoolean("camera_photo",selectedPhoto);state.putBoolean("camera_auto",autoAnalyze);
        super.onSaveInstanceState(state);
    }
    /**
     * Libera el ejecutor y, cuando corresponde, retira las comprobaciones pendientes de sesión.
     */
    @Override protected void onDestroy() { idleHandler.removeCallbacks(idleCheck);worker.shutdown();super.onDestroy(); }
}
