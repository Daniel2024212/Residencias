package com.example.evogmd;

import android.graphics.*;
import com.example.evogmd.imagen.Imagen;

/** Dibuja exclusivamente los píxeles del borde de la ROI real calculada. */
public final class RoiContour {
    private RoiContour(){}
    public static Bitmap draw(Imagen image){
        Bitmap original=image.getBitmapOriginal();
        Bitmap mask=image.getBitmapMascara();
        int w=original.getWidth(),h=original.getHeight();
        int[] pixels=new int[w*h];mask.getPixels(pixels,0,w,0,0,w,h);
        Bitmap result=original.copy(Bitmap.Config.ARGB_8888,true);
        Canvas canvas=new Canvas(result);
        Paint line=new Paint(Paint.ANTI_ALIAS_FLAG);line.setColor(Color.rgb(0,220,160));line.setStyle(Paint.Style.STROKE);line.setStrokeWidth(2.5f);
        // Detección de frontera: cambios claro/oscuro entre vecinos de la máscara.
        for(int y=1;y<h-1;y++)for(int x=1;x<w-1;x++){
            int i=y*w+x;
            boolean on=(pixels[i]&0xFF)>127;
            if(on && (((pixels[i-1]&0xFF)<=127)||((pixels[i+1]&0xFF)<=127)||((pixels[i-w]&0xFF)<=127)||((pixels[i+w]&0xFF)<=127))){
                canvas.drawPoint(x,y,line);
            }
        }
        mask.recycle();original.recycle();return result;
    }
}
