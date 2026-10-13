package com.example.evogmd.report;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.pdf.PdfRenderer;
import android.os.ParcelFileDescriptor;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import com.example.evogmd.R;
import com.example.evogmd.login.Session;
import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/** Visor PDF integrado con páginas renderizadas en un executor; evita depender de aplicaciones externas. */
public final class PdfPreview {
    private PdfPreview(){}
    /** Abre el PDF privado del resultado seleccionado. El controlador debe verificar el propietario previamente. */
    public static void show(Activity activity,File file){
        if(!Session.isActive(activity))return;
        View view=activity.getLayoutInflater().inflate(R.layout.dialog_pdf_preview,null);
        ImageView image=view.findViewById(R.id.pdf_page);
        TextView status=view.findViewById(R.id.pdf_page_status);
        Button previous=view.findViewById(R.id.pdf_previous),next=view.findViewById(R.id.pdf_next);
        ExecutorService worker=Executors.newSingleThreadExecutor();AtomicBoolean closed=new AtomicBoolean(false);
        int[] position={0},pages={0};Bitmap[] displayed={null};
        AlertDialog dialog=new Session.DialogBuilder(activity).setTitle("Resultado PDF").setView(view).setPositiveButton("Cerrar",null).create();
        Runnable render=() -> {
            previous.setEnabled(false);next.setEnabled(false);status.setText("Abriendo PDF…");
            final int pageIndex=position[0];
            worker.execute(() -> {
                Bitmap bitmap=null;
                try(ParcelFileDescriptor fd=ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY);PdfRenderer pdf=new PdfRenderer(fd)){
                    int count=pdf.getPageCount();
                    if(count==0)throw new java.io.IOException("El PDF no contiene páginas.");
                    try(PdfRenderer.Page page=pdf.openPage(pageIndex)){
                        int width=Math.min(1200,Math.max(600,activity.getResources().getDisplayMetrics().widthPixels));
                        int height=Math.round(width*page.getHeight()/(float)page.getWidth());
                        bitmap=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);bitmap.eraseColor(Color.WHITE);
                        page.render(bitmap,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                    }
                    final Bitmap ready=bitmap;bitmap=null;
                    activity.runOnUiThread(() -> {
                        if(closed.get()||activity.isDestroyed()){ready.recycle();return;}
                        Bitmap old=displayed[0];displayed[0]=ready;image.setImageBitmap(ready);if(old!=null)old.recycle();
                        pages[0]=count;status.setText("Página "+(pageIndex+1)+" de "+count);
                        previous.setEnabled(pageIndex>0);next.setEnabled(pageIndex+1<count);
                    });
                }catch(Exception e){
                    if(bitmap!=null)bitmap.recycle();
                    activity.runOnUiThread(() -> {if(!closed.get()&&!activity.isDestroyed())status.setText("No se pudo leer el PDF. Vuelve a analizar la imagen para regenerarlo.");});
                }
            });
        };
        previous.setOnClickListener(v -> {if(position[0]>0){position[0]--;render.run();}});
        next.setOnClickListener(v -> {if(position[0]+1<pages[0]){position[0]++;render.run();}});
        dialog.setOnDismissListener(d -> {closed.set(true);worker.shutdown();image.setImageDrawable(null);if(displayed[0]!=null){displayed[0].recycle();displayed[0]=null;}});
        dialog.show();
        if(dialog.getWindow()!=null)dialog.getWindow().setLayout(
            (int)(activity.getResources().getDisplayMetrics().widthPixels*0.94),
            (int)(activity.getResources().getDisplayMetrics().heightPixels*0.85));
        render.run();
    }
}
