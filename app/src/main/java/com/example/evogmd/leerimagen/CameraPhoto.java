/*
 * DOCUMENTACIÓN DEL ARCHIVO
 * Decodifica fotos y aplica orientación EXIF sin controlar la exposición de la cámara.
 */
package com.example.evogmd.leerimagen;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import java.io.IOException;
import java.io.InputStream;

/** Normaliza únicamente capturas de cámara; no cambia imágenes del dataset. */
public final class CameraPhoto {
    /**
     * Impide crear instancias de esta clase de utilidades.
     */
    private CameraPhoto() {}
    /**
     * Decodifica la captura con muestreo para limitar memoria y corrige su orientación EXIF. No altera brillo, contraste ni saturación.
     * 
     * @param context contexto Android para acceder a recursos y almacenamiento privado.
     * @param uri URI del documento que se consulta.
     * @return bitmap resultante de la conversión o consulta.
     * @throws IOException si no puede completarse la lectura, validación o procesamiento solicitado.
     */
    public static Bitmap open(Context context, Uri uri) throws IOException {
        BitmapFactory.Options options=new BitmapFactory.Options();options.inJustDecodeBounds=true;
        try(InputStream in=context.getContentResolver().openInputStream(uri)) { BitmapFactory.decodeStream(in,null,options); }
        if(options.outWidth<=0 || options.outHeight<=0) throw new IOException("La cámara no guardó una foto válida.");
        options.inSampleSize=1;
        while(Math.max(options.outWidth,options.outHeight)/options.inSampleSize>2048) options.inSampleSize*=2;
        options.inJustDecodeBounds=false;
        int orientation;
        try(InputStream in=context.getContentResolver().openInputStream(uri)) {
            if(in==null) throw new IOException("No se pudo abrir la captura.");
            orientation=new ExifInterface(in).getAttributeInt(ExifInterface.TAG_ORIENTATION,ExifInterface.ORIENTATION_NORMAL);
        }
        Bitmap photo;
        try(InputStream in=context.getContentResolver().openInputStream(uri)) { photo=BitmapFactory.decodeStream(in,null,options); }
        if(photo==null) throw new IOException("No se pudo leer la captura.");
        Matrix m=new Matrix();
        switch(orientation) {
            case 2: m.setScale(-1,1);break;
            case 3: m.setRotate(180);break;
            case 4: m.setScale(1,-1);break;
            case 5: m.setRotate(90);m.postScale(-1,1);break;
            case 6: m.setRotate(90);break;
            case 7: m.setRotate(-90);m.postScale(-1,1);break;
            case 8: m.setRotate(-90);break;
            default: return photo;
        }
        Bitmap normalized=Bitmap.createBitmap(photo,0,0,photo.getWidth(),photo.getHeight(),m,true);
        if(normalized!=photo) photo.recycle();
        return normalized;
    }
}
