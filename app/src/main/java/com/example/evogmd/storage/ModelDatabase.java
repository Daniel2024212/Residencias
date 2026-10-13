/*
 * DOCUMENTACIÓN DEL ARCHIVO
 * Instala y consulta evogmd_modelos.db desde assets; almacena modelos serializados y relaciona imágenes con máscaras por hash.
 */
package com.example.evogmd.storage;
import android.content.Context;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import java.io.*;
import java.security.MessageDigest;
import java.util.Locale;
import com.example.evogmd.login.Session;

/** Base preempaquetada: se instala una vez. No entrena ni cambia los pesos SVM. */
public final class ModelDatabase implements AutoCloseable {
    private final SQLiteDatabase db;
    public static final String RESULTS_SCHEMA = "CREATE TABLE IF NOT EXISTS resultados_clasificacion ("
        + "id TEXT PRIMARY KEY, usuario_id INTEGER NOT NULL, nombre_archivo TEXT NOT NULL, "
        + "clasificacion TEXT NOT NULL CHECK(clasificacion IN ('Baja probabilidad','Alta probabilidad')), "
        + "imagen BLOB NOT NULL, imagen_sha256 TEXT NOT NULL, tipo_imagen TEXT NOT NULL, "
        + "fecha TEXT NOT NULL, pdf_ruta TEXT NOT NULL)";

    /** Guarda la imagen original y el resultado en la misma base del catálogo. Solo el propietario autenticado. */
    public void saveResult(Context context,long owner,com.example.evogmd.report.ResultReport report) throws Exception {
        com.example.evogmd.login.DatabaseHelper.User user=Session.current(context);
        if(user==null||user.id!=owner)throw new SecurityException("La sesión terminó. Inicia sesión para guardar los pendientes.");
        File allowed=new File(context.getFilesDir(),"reports/"+owner+"/"+report.id());
        if(!allowed.getCanonicalFile().equals(report.folder.getCanonicalFile()))throw new SecurityException("Reporte ajeno a la cuenta.");
        if(report.photo().length()>32L*1024*1024)throw new IOException("La imagen supera el límite local de 32 MiB. El PDF sigue disponible.");
        ByteArrayOutputStream data=new ByteArrayOutputStream();
        try(InputStream in=new FileInputStream(report.photo())){com.example.evogmd.report.ResultReport.copy(in,data);}
        byte[] image=data.toByteArray();String hash=sha256(image);
        ContentValues cv=new ContentValues();cv.put("id",report.id());cv.put("usuario_id",owner);
        cv.put("nombre_archivo",report.filename());
         String classification=report.meta.getString("classification");
         if ("Benigno".equals(classification)) classification="Baja probabilidad";
         else if ("Maligno".equals(classification)) classification="Alta probabilidad";
         cv.put("clasificacion",classification);
        cv.put("imagen",image);cv.put("imagen_sha256",hash);cv.put("tipo_imagen",report.meta.optString("mime"));
        cv.put("fecha",report.meta.getString("created_at"));cv.put("pdf_ruta",report.pdf().getAbsolutePath());
        db.beginTransaction();
        try{
            db.insertWithOnConflict("resultados_clasificacion",null,cv,SQLiteDatabase.CONFLICT_IGNORE);
            // Confirmar identidad antes de marcar un reintento como guardado; no leer BLOB en CursorWindow.
            try(Cursor c=db.rawQuery("SELECT usuario_id,imagen_sha256,nombre_archivo,clasificacion FROM resultados_clasificacion WHERE id=?",new String[]{report.id()})){
                if(!c.moveToFirst()||c.getLong(0)!=owner||!hash.equals(c.getString(1))||!report.filename().equals(c.getString(2))||!classification.equals(c.getString(3)))
                    throw new IOException("No se pudo confirmar el resultado en SQLite.");
            }
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }

    /**
     * Inicializa ModelDatabase con los datos recibidos y establece su estado de trabajo.
     * 
     * @param context contexto Android para acceder a recursos y almacenamiento privado.
     * @throws IOException si no puede completarse la lectura, validación o procesamiento solicitado.
     */
    public ModelDatabase(Context context) throws IOException {
        File path=context.getDatabasePath("evogmd_modelos.db");
        synchronized(ModelDatabase.class) {
            if(!path.exists()) {
                File parent=path.getParentFile();
                if(!parent.exists() && !parent.mkdirs()) throw new IOException("No se pudo preparar SQLite");
                File temp=new File(parent,"evogmd_modelos.db.tmp");
                try(InputStream in=context.getAssets().open("evogmd_modelos.db"); OutputStream out=new FileOutputStream(temp)) {
                    byte[] block=new byte[65536]; int n;
                    while((n=in.read(block))!=-1) out.write(block,0,n);
                }
                if(!temp.renameTo(path)) throw new IOException("No se pudo instalar la base de datos");
            }
        }
        db=SQLiteDatabase.openDatabase(path.getAbsolutePath(),null,SQLiteDatabase.OPEN_READWRITE);
        db.setForeignKeyConstraintsEnabled(true);
        // Migración aditiva también para instalaciones previas: conserva catálogo y modelos.
        migrateResultLabels();
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_resultados_usuario_fecha ON resultados_clasificacion(usuario_id,fecha)");
    }
    /** Actualiza únicamente las etiquetas almacenadas; preserva reportes, fotografías y modelos. */
    private void migrateResultLabels() {
        String oldDefinition = null;
        try (Cursor cursor = db.rawQuery(
                "SELECT sql FROM sqlite_master WHERE type='table' AND name='resultados_clasificacion'", null)) {
            if (cursor.moveToFirst()) oldDefinition = cursor.getString(0);
        }
        if (oldDefinition == null) {
            db.execSQL(RESULTS_SCHEMA);
            return;
        }
        if (!oldDefinition.contains("'Benigno'") && !oldDefinition.contains("'Maligno'")) return;
        db.beginTransaction();
        try {
            String tempSchema = RESULTS_SCHEMA.replace("resultados_clasificacion (", "resultados_clasificacion_nueva (");
            db.execSQL(tempSchema);
            db.execSQL("INSERT INTO resultados_clasificacion_nueva "
                    + "(id,usuario_id,nombre_archivo,clasificacion,imagen,imagen_sha256,tipo_imagen,fecha,pdf_ruta) "
                    + "SELECT id,usuario_id,nombre_archivo,CASE clasificacion "
                    + "WHEN 'Benigno' THEN 'Baja probabilidad' "
                    + "WHEN 'Maligno' THEN 'Alta probabilidad' ELSE clasificacion END, "
                    + "imagen,imagen_sha256,tipo_imagen,fecha,pdf_ruta FROM resultados_clasificacion");
            db.execSQL("DROP TABLE resultados_clasificacion");
            db.execSQL("ALTER TABLE resultados_clasificacion_nueva RENAME TO resultados_clasificacion");
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }

    /** Instala el modelo BEST Java 8 V1 solo si sigue activo el V5 legado.
     * Conserva los modelos importados por el administrador y demás datos SQLite. */
    public void upgradeLegacyV5(byte[] bundledV1) throws Exception {
        boolean upgrade;
        try (Cursor c = db.rawQuery("SELECT nombre FROM modelos WHERE activo=1 LIMIT 1", null)) {
            upgrade = !c.moveToFirst() || "SVM BEST V5".equals(c.getString(0));
        }
        if (upgrade) {
            activate(bundledV1, "SVM BEST JAVA 8 V1 · Test 91.67%",
                    "Reporte original: test 22/24 (91.67%), 80/20; no reentrenado");
        }
    }

    /**
     * Lee los bytes del modelo activo y verifica que su SHA-256 coincida con el almacenado.
     * 
     * @return resultado de la operación descrita.
     * @throws Exception si no puede completarse la lectura, validación o procesamiento solicitado.
     */
    public byte[] activeModel() throws Exception {
        try(Cursor c=db.rawQuery("SELECT contenido,sha256 FROM modelos WHERE activo=1",null)) {
            if(!c.moveToFirst()) throw new IOException("No hay un modelo activo");
            byte[] data=c.getBlob(0);
            if(!sha256(data).equals(c.getString(1))) throw new IOException("El modelo no supera la verificación de integridad");
            return data;
        }
    }
    /**
     * Construye el resumen del modelo; las cantidades de imágenes y máscaras están escritas como texto fijo en esta versión.
     * 
     * @return texto resultante de la operación descrita.
     */
    public String summary() {
        try(Cursor c=db.rawQuery("SELECT nombre,sha256,origen FROM modelos WHERE activo=1",null)) {
            if(!c.moveToFirst()) return "Sin modelo activo";
            long images=0, masks=0;
            try(Cursor count=db.rawQuery("SELECT (SELECT COUNT(*) FROM imagenes), (SELECT COUNT(*) FROM mascaras)",null)) {
                if(count.moveToFirst()) { images=count.getLong(0); masks=count.getLong(1); }
            }
            return c.getString(0)+"\n"+c.getString(2)+"\nSHA-256: "+c.getString(1)+"\n"+images+" imágenes · "+masks+" máscaras";
        }
    }
    /**
     * Comprueba si existe la marca de migración del modelo original.
     * 
     * @return true cuando se cumple la condición descrita; false en caso contrario.
     */
    public boolean migrated() {
        try(Cursor c=db.rawQuery("SELECT valor FROM meta WHERE clave='migracion_modelo_original'",null)) { return c.moveToFirst(); }
    }
    /**
     * Registra que la migración del modelo original ya fue atendida.
     */
    public void markMigrated() { db.execSQL("INSERT OR REPLACE INTO meta VALUES('migracion_modelo_original','1')"); }
    /**
     * Exige rol ADMIN, activa el modelo recibido y registra la migración.
     * 
     * @param context contexto Android para acceder a recursos y almacenamiento privado.
     * @param bytes contenido binario del modelo.
     * @param name nombre asociado a la cuenta o al recurso.
     * @throws Exception si no puede completarse la lectura, validación o procesamiento solicitado.
     */
    public void importAdmin(Context context, byte[] bytes, String name) throws Exception {
        Session.requireAdmin(context); activate(bytes,name,"Importado por administrador"); markMigrated();
    }
    /**
     * Guarda el modelo de una instalación anterior y lo marca como migrado sin volver a entrenarlo.
     * 
     * @param bytes contenido binario del modelo.
     * @throws Exception si no puede completarse la lectura, validación o procesamiento solicitado.
     */
    public void preserveOriginal(byte[] bytes) throws Exception {
        activate(bytes,"Modelo original del dispositivo","Migrado sin reentrenar desde la instalación anterior"); markMigrated();
    }
    /**
     * Desactiva el modelo anterior e inserta el nuevo con su hash en una transacción SQLite.
     * 
     * @param bytes contenido binario del modelo.
     * @param name nombre asociado a la cuenta o al recurso.
     * @param origin descripción del origen del modelo importado.
     * @throws Exception si no puede completarse la lectura, validación o procesamiento solicitado.
     */
    private void activate(byte[] bytes,String name,String origin) throws Exception {
        ContentValues cv=new ContentValues();cv.put("nombre",name);cv.put("contenido",bytes);
        cv.put("sha256",sha256(bytes));cv.put("activo",1);cv.put("origen",origin);
        db.beginTransaction();
        try { db.execSQL("UPDATE modelos SET activo=0");db.insertOrThrow("modelos",null,cv);db.setTransactionSuccessful(); }
        finally { db.endTransaction(); }
    }
    /**
     * Busca una máscara experta mediante el SHA-256 exacto de la imagen; devuelve null si no hay coincidencia.
     * 
     * @param hash SHA-256 exacto de la imagen.
     * @return bitmap resultante de la conversión o consulta.
     */
    public Bitmap maskForHash(String hash) {
        try(Cursor c=db.rawQuery("SELECT m.png FROM imagenes i JOIN mascaras m ON m.codigo=i.codigo_mascara WHERE i.sha256=?",new String[]{hash})) {
            if(!c.moveToFirst()) return null;
            byte[] bytes=c.getBlob(0);return BitmapFactory.decodeByteArray(bytes,0,bytes.length);
        }
    }
    /**
     * Consulta los nombres y clases de las imágenes; requiere sesión de administrador.
     * 
     * @param c cursor o contexto Android, según el tipo de la firma.
     * @return resultado de la operación descrita.
     */
    public java.util.List<String> imageNames(Context c) {
        Session.requireAdmin(c);java.util.List<String> names=new java.util.ArrayList<>();
        try(Cursor cur=db.rawQuery("SELECT nombre,clase FROM imagenes ORDER BY nombre",null)) {
            while(cur.moveToNext()) names.add(cur.getString(0)+" · "+cur.getString(1));
        }
        return names;
    }
    /**
     * Calcula el resumen SHA-256 de los bytes y lo expresa en hexadecimal.
     * 
     * @param bytes contenido binario del modelo.
     * @return texto resultante de la operación descrita.
     * @throws Exception si no puede completarse la lectura, validación o procesamiento solicitado.
     */
    public static String sha256(byte[] bytes) throws Exception { return hex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
    /**
     * Calcula SHA-256 por bloques sin cargar todo el archivo en memoria; el llamador cierra el flujo.
     * 
     * @param in flujo de entrada abierto por el llamador.
     * @return texto resultante de la operación descrita.
     * @throws Exception si no puede completarse la lectura, validación o procesamiento solicitado.
     */
    public static String hashStream(InputStream in) throws Exception {
        MessageDigest d=MessageDigest.getInstance("SHA-256");byte[] b=new byte[65536];int n;
        while((n=in.read(b))!=-1) d.update(b,0,n);return hex(d.digest());
    }
    /**
     * Convierte cada byte a dos dígitos hexadecimales sin signo.
     * 
     * @param data intent con el resultado; puede ser null para la cámara.
     * @return texto resultante de la operación descrita.
     */
    private static String hex(byte[] data) {
        StringBuilder out=new StringBuilder();for(byte b:data) out.append(String.format(Locale.US,"%02x",b&255));return out.toString();
    }
    /**
     * Lee un flujo de modelo con un límite de 16 MiB; rechaza flujos nulos o archivos que excedan ese tamaño.
     * 
     * @param in flujo de entrada abierto por el llamador.
     * @return resultado de la operación descrita.
     * @throws IOException si no puede completarse la lectura, validación o procesamiento solicitado.
     */
    public static byte[] readModel(InputStream in) throws IOException {
        if(in==null) throw new IOException("No se pudo abrir el modelo");
        ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[8192];int n;
        while((n=in.read(b))!=-1) { if(out.size()+n>16*1024*1024) throw new IOException("El modelo supera 16 MB");out.write(b,0,n); }
        return out.toByteArray();
    }
    /**
     * Cierra la conexión SQLite administrada por esta instancia.
     */
    @Override public void close() { db.close(); }
}
