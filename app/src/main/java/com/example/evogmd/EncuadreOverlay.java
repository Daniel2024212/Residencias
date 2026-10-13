package com.example.evogmd;
import android.content.Context;
import android.graphics.*;
import android.util.AttributeSet;
import android.view.View;
/** Líneas con forma de # y sombreado exterior; centro equivale a la región recortada. */
public class EncuadreOverlay extends View {
    private final Paint p=new Paint(3);
    public EncuadreOverlay(Context c, AttributeSet a){super(c,a);setLayerType(View.LAYER_TYPE_SOFTWARE,null);}
    @Override protected void onDraw(Canvas c){super.onDraw(c);
        float side=Math.min(getWidth(),getHeight())*0.60f,cx=getWidth()/2f,cy=getHeight()/2f;
        float left=cx-side/2,right=cx+side/2,top=cy-side/2,bottom=cy+side/2;
        p.setStyle(Paint.Style.FILL);p.setColor(0x66000000);
        c.drawRect(0,0,getWidth(),top,p);c.drawRect(0,bottom,getWidth(),getHeight(),p);
        c.drawRect(0,top,left,bottom,p);c.drawRect(right,top,getWidth(),bottom,p);
        p.setColor(0xFF79F4E5);p.setStrokeWidth(3*getResources().getDisplayMetrics().density);p.setStyle(Paint.Style.STROKE);
        c.drawLine(left,0,left,getHeight(),p);c.drawLine(right,0,right,getHeight(),p);
        c.drawLine(0,top,getWidth(),top,p);c.drawLine(0,bottom,getWidth(),bottom,p);
    }
}
