package com.example.evogmd.report;

import android.content.Context;
import android.content.ContentValues;
import android.content.ContentResolver;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.database.Cursor;
import android.content.ContentUris;
import android.graphics.*;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import org.json.JSONObject;
import java.io.*;
import java.util.*;
import java.text.SimpleDateFormat;

/** Copia privada e inmutable del resultado; permite guardar el PDF y reintentar el guardado local. */
public final class ResultReport {
    public final File folder;
    public final JSONObject meta;
    private ResultReport(File folder, JSONObject meta) { this.folder=folder;this.meta=meta; }
    public File photo(){return new File(folder,"image.bin");}
    public File pdf(){return new File(folder,"resultado.pdf");}
    public String id(){return meta.optString("id");}
    public String filename(){return meta.optString("filename");}
    /** Crea imagen, metadatos y PDF antes de publicar el directorio como un reporte completo. */
    public static ResultReport create(Context c,long user,Uri source,Bitmap image,String filename,String label) throws Exception {
        File parent=new File(c.getFilesDir(),"reports/"+user);if(!parent.isDirectory()&&!parent.mkdirs())throw new IOException("No se pudo preparar el reporte.");
        String id=UUID.randomUUID().toString();File temporary=new File(parent,id+".tmp");temporary.mkdir();
        JSONObject m=new JSONObject();m.put("id",id);m.put("filename",filename==null||filename.isEmpty()?"imagen":filename);m.put("classification",label);
        m.put("created_at",new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z",Locale.US).format(new Date()));
        String mime=c.getContentResolver().getType(source);m.put("mime",mime==null?"application/octet-stream":mime);
        ResultReport r=new ResultReport(temporary,m);
        try {
            try(InputStream in=c.getContentResolver().openInputStream(source);OutputStream out=new FileOutputStream(r.photo())) {
                if(in==null)throw new IOException("No se pudo copiar la imagen.");copy(in,out);
            }
            writePdf(r,image);
            r.validatePdf();
            try(Writer w=new OutputStreamWriter(new FileOutputStream(new File(temporary,"meta.json")),"UTF-8")){w.write(m.toString());}
            File complete=new File(parent,id);if(!temporary.renameTo(complete))throw new IOException("No se pudo conservar el reporte.");
            return new ResultReport(complete,m);
        } catch(Exception e){File[] files=temporary.listFiles();if(files!=null)for(File f:files)f.delete();temporary.delete();throw e;}
    }
    /** Guarda una copia visible desde Archivos > Documentos > EvoGMD > ResultadosPDF. */
    public Uri saveUserPdf(Context context,long userId) throws IOException {
        validatePdf();
        final String name="EvoGMD_usuario_"+userId+"_"+id()+".pdf";
        final String subdir=Environment.DIRECTORY_DOCUMENTS+"/EvoGMD/ResultadosPDF/";
        if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.Q){
            ContentResolver resolver=context.getContentResolver();
            Uri collection=MediaStore.Files.getContentUri("external");
            // Idempotente: pulsar dos veces no crea dos PDF del mismo resultado.
            Uri existing=null;
            try(Cursor cursor=resolver.query(collection,new String[]{MediaStore.Files.FileColumns._ID},
                    MediaStore.Files.FileColumns.DISPLAY_NAME+"=? AND "+MediaStore.Files.FileColumns.RELATIVE_PATH+"=?",
                    new String[]{name,subdir},null)){
                if(cursor!=null&&cursor.moveToFirst())existing=ContentUris.withAppendedId(collection,cursor.getLong(0));
            }
            if(existing!=null)return existing;
            ContentValues values=new ContentValues();
            values.put(MediaStore.Files.FileColumns.DISPLAY_NAME,name);
            values.put(MediaStore.Files.FileColumns.MIME_TYPE,"application/pdf");
            values.put(MediaStore.Files.FileColumns.RELATIVE_PATH,subdir);
            values.put(MediaStore.Files.FileColumns.IS_PENDING,1);
            Uri destination=resolver.insert(collection,values);
            if(destination==null)throw new IOException("No se pudo crear el PDF en Documentos.");
            boolean done=false;
            try{
                try(InputStream in=new FileInputStream(pdf());OutputStream out=resolver.openOutputStream(destination,"w")){
                    if(out==null)throw new IOException("No se pudo escribir el PDF público.");
                    copy(in,out);out.flush();
                }
                ContentValues ready=new ContentValues();ready.put(MediaStore.Files.FileColumns.IS_PENDING,0);
                if(resolver.update(destination,ready,null,null)<1)throw new IOException("No se pudo publicar el PDF.");
                done=true;
                return destination;
            } finally {if(!done)resolver.delete(destination,null,null);}
        }
        // Android 7 a 9: escritura directa en Documents (requiere WRITE_EXTERNAL_STORAGE).
        if(context.checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                !=android.content.pm.PackageManager.PERMISSION_GRANTED)
            throw new IOException("Autoriza el permiso de almacenamiento para guardar en Documentos.");
        File dir=new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),"EvoGMD/ResultadosPDF");
        if(!dir.isDirectory()&&!dir.mkdirs())throw new IOException("No se pudo crear Documentos/EvoGMD/ResultadosPDF.");
        File destination=new File(dir,name);
        if(!destination.isFile()){
            File temporary=new File(dir,name+".tmp");
            try{
                try(InputStream in=new FileInputStream(pdf());OutputStream out=new FileOutputStream(temporary)){copy(in,out);out.flush();}
                if(!temporary.renameTo(destination))throw new IOException("No se pudo finalizar el PDF público.");
            } finally {if(temporary.exists())temporary.delete();}
        }
        return Uri.fromFile(destination);
    }
    /** Recupera únicamente un UUID del directorio privado del usuario; resiste recreaciones del selector de archivos. */
    public static ResultReport load(Context c,long owner,String id)throws Exception{
        if(id==null||!UUID.fromString(id).toString().equals(id))throw new IOException("Reporte no disponible.");
        File dir=new File(c.getFilesDir(),"reports/"+owner+"/"+id);
        try(Reader reader=new InputStreamReader(new FileInputStream(new File(dir,"meta.json")),"UTF-8")){
            StringBuilder s=new StringBuilder();char[] b=new char[2048];int n;while((n=reader.read(b))!=-1)s.append(b,0,n);
            ResultReport r=new ResultReport(dir,new JSONObject(s.toString()));
            if(!id.equals(r.id()))throw new IOException("Identificador de reporte inválido.");return r;
        }
    }
    /** Comprueba la firma y que Android pueda interpretar al menos una página del PDF. Ejecutar fuera del hilo principal. */
    public void validatePdf()throws IOException{
        if(!pdf().isFile()||pdf().length()<8)throw new IOException("El PDF está vacío. Vuelve a analizar la imagen.");
        try(InputStream in=new FileInputStream(pdf())){byte[] h=new byte[5];if(in.read(h)!=5||!"%PDF-".equals(new String(h,"US-ASCII")))throw new IOException("El archivo no contiene un PDF válido.");}
        try(android.os.ParcelFileDescriptor fd=android.os.ParcelFileDescriptor.open(pdf(),android.os.ParcelFileDescriptor.MODE_READ_ONLY);
            android.graphics.pdf.PdfRenderer renderer=new android.graphics.pdf.PdfRenderer(fd)){
            if(renderer.getPageCount()<1)throw new IOException("El PDF no contiene páginas.");
        }
    }
    /** Copia por bloques, sin mantener todo el archivo en memoria. */
    public static void copy(InputStream in,OutputStream out)throws IOException{byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1)out.write(b,0,n);}
    /** A4: imagen sin deformación y metadatos con salto de página para nombres largos. */
    private static void writePdf(ResultReport r,Bitmap image)throws Exception {
        PdfDocument doc=new PdfDocument();
        try {
            PdfDocument.Page page=doc.startPage(new PdfDocument.PageInfo.Builder(595,842,1).create());Canvas canvas=page.getCanvas();
            Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);p.setColor(Color.rgb(8,127,140));p.setTextSize(24);canvas.drawText("Evo-GMD · Resultado",36,50,p);
            float scale=Math.min(523f/image.getWidth(),430f/image.getHeight());float w=image.getWidth()*scale,h=image.getHeight()*scale;
            canvas.drawBitmap(image,null,new RectF((595-w)/2,80,(595+w)/2,80+h),new Paint(Paint.FILTER_BITMAP_FLAG));
            p.setColor(Color.rgb(23,45,57));p.setTextSize(14);int y=540,pageNumber=1;
            String content="Archivo: "+r.filename()+"\nClasificación: "+r.meta.getString("classification")+"\nFecha: "+r.meta.getString("created_at")+"\nIdentificador: "+r.id()+"\n\nClasificación experimental. No sustituye una valoración médica.";
            for(String line:content.replace('\r',' ').split("\n",-1)) {
                if(line.isEmpty()){y+=22;continue;}
                int start=0;
                while(start<line.length()){
                    if(y>790){doc.finishPage(page);page=doc.startPage(new PdfDocument.PageInfo.Builder(595,842,++pageNumber).create());canvas=page.getCanvas();y=45;}
                    int n=Math.max(1,p.breakText(line,start,line.length(),true,523,null));canvas.drawText(line.substring(start,start+n),36,y,p);y+=22;start+=n;
                }
            }
            doc.finishPage(page);try(OutputStream out=new FileOutputStream(r.pdf())){doc.writeTo(out);}
        } finally{doc.close();}
    }
    /** Solo devuelve reportes completos del usuario actual que todavía no tienen confirmación de SQLite. */
    public static java.util.List<ResultReport> pending(Context c,long user)throws Exception{
        java.util.List<ResultReport> list=new ArrayList<>();File[] dirs=new File(c.getFilesDir(),"reports/"+user).listFiles();
        if(dirs!=null)for(File dir:dirs){
            if(!dir.isDirectory()||dir.getName().endsWith(".tmp")||new File(dir,"stored_sqlite").exists())continue;
            try(Reader reader=new InputStreamReader(new FileInputStream(new File(dir,"meta.json")),"UTF-8")){
                StringBuilder s=new StringBuilder();char[] b=new char[2048];int n;while((n=reader.read(b))!=-1)s.append(b,0,n);
                list.add(new ResultReport(dir,new JSONObject(s.toString())));
            }
        }return list;
    }
    /** Se invoca únicamente después de validar la confirmación de la transacción SQLite. */
    public void markStored()throws IOException{if(!new File(folder,"stored_sqlite").createNewFile()&&!new File(folder,"stored_sqlite").exists())throw new IOException("No se pudo registrar el guardado.");}
}
