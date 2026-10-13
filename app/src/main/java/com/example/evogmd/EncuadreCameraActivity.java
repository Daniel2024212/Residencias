package com.example.evogmd;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.hardware.Camera;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import java.io.*;

/** Cámara integrada: cuadrícula # en vivo, captura normal y recorte central. */
@SuppressWarnings("deprecation")
public class EncuadreCameraActivity extends Activity implements SurfaceHolder.Callback {
    private Camera camera;
    private SurfaceView surface;
    private boolean capturing;
    private String output;
    private int cameraId;
    private int pictureRotation=90;
    private final int permission=180;
    @Override public void onCreate(Bundle b){super.onCreate(b);
        output=getIntent().getStringExtra("output_path");
        setContentView(R.layout.activity_encuadre_camera);
        surface=findViewById(R.id.camera_surface);
        surface.getHolder().addCallback(this);
        findViewById(R.id.camera_cancel).setOnClickListener(v->finish());
        findViewById(R.id.camera_shoot).setOnClickListener(v->shoot());
        if(checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.CAMERA},permission);
    }
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] result){super.onRequestPermissionsResult(request,permissions,result);
        if(request==permission){if(result.length==0||result[0]!=PackageManager.PERMISSION_GRANTED){Toast.makeText(this,"Se necesita permiso de cámara",Toast.LENGTH_LONG).show();finish();}else startCamera();}
    }
    @Override public void surfaceCreated(SurfaceHolder h){startCamera();}
    @Override public void surfaceChanged(SurfaceHolder h,int format,int w,int height){if(camera!=null){try{camera.stopPreview();camera.setPreviewDisplay(h);camera.startPreview();}catch(Exception e){showError();}}}
    @Override public void surfaceDestroyed(SurfaceHolder h){stopCamera();}
    @Override protected void onPause(){stopCamera();super.onPause();}
    @Override protected void onResume(){super.onResume();if(surface!=null&&surface.getHolder().getSurface().isValid())startCamera();}
    private void startCamera(){if(camera!=null||surface==null||!surface.getHolder().getSurface().isValid()||checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED)return;
        try{
            cameraId=0;
            for(int i=0;i<Camera.getNumberOfCameras();i++){Camera.CameraInfo x=new Camera.CameraInfo();Camera.getCameraInfo(i,x);if(x.facing==Camera.CameraInfo.CAMERA_FACING_BACK){cameraId=i;break;}}
            camera=Camera.open(cameraId);
            Camera.CameraInfo info=new Camera.CameraInfo();Camera.getCameraInfo(cameraId,info);
            int rotation=getWindowManager().getDefaultDisplay().getRotation();int degrees=rotation==Surface.ROTATION_90?90:rotation==Surface.ROTATION_180?180:rotation==Surface.ROTATION_270?270:0;
            pictureRotation=(info.orientation-degrees+360)%360;
            camera.setDisplayOrientation((info.orientation-degrees+360)%360);
            Camera.Parameters params=camera.getParameters();params.setRotation(0);
            if(params.getSupportedFocusModes().contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE))params.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
            camera.setParameters(params);camera.setPreviewDisplay(surface.getHolder());camera.startPreview();
        }catch(Exception e){stopCamera();showError();}
    }
    private void shoot(){if(camera==null||capturing)return;capturing=true;findViewById(R.id.camera_shoot).setEnabled(false);
        try{camera.takePicture(null,null,(bytes,c)->new Thread(()->{
            try{
                BitmapFactory.Options opts=new BitmapFactory.Options();opts.inSampleSize=2;
                Bitmap bitmap=BitmapFactory.decodeByteArray(bytes,0,bytes.length,opts);
                if(bitmap==null)throw new IOException("Fotografía no válida");
                // Los bytes JPEG incluyen el giro configurado. La decodificación bitmap no aplica EXIF.
                Matrix matrix=new Matrix();matrix.postRotate(pictureRotation);
                Bitmap upright=Bitmap.createBitmap(bitmap,0,0,bitmap.getWidth(),bitmap.getHeight(),matrix,true);
                if(upright!=bitmap)bitmap.recycle();
                // La región entre las líneas exteriores del # ocupa 60% de la menor dimensión.
                int side=Math.round(Math.min(upright.getWidth(),upright.getHeight())*0.60f);
                int x=(upright.getWidth()-side)/2,y=(upright.getHeight()-side)/2;
                Bitmap cropped=Bitmap.createBitmap(upright,x,y,side,side);
                if(cropped!=upright)upright.recycle();
                File target=new File(output);File parent=target.getParentFile();if(parent!=null&&!parent.exists()&&!parent.mkdirs())throw new IOException("No se pudo preparar la fotografía");
                try(FileOutputStream out=new FileOutputStream(target)){if(!cropped.compress(Bitmap.CompressFormat.JPEG,95,out))throw new IOException("Error al guardar la foto");}
                cropped.recycle();runOnUiThread(()->{setResult(RESULT_OK,new Intent());finish();});
            }catch(Exception ex){runOnUiThread(()->{capturing=false;findViewById(R.id.camera_shoot).setEnabled(true);Toast.makeText(this,"No se pudo guardar la captura",Toast.LENGTH_LONG).show();try{camera.startPreview();}catch(Exception ignored){}});}
        }).start());}catch(Exception e){capturing=false;findViewById(R.id.camera_shoot).setEnabled(true);showError();}
    }
    private void stopCamera(){if(camera!=null){try{camera.stopPreview();}catch(Exception ignored){}camera.release();camera=null;}}
    private void showError(){Toast.makeText(this,"No se pudo abrir la cámara",Toast.LENGTH_LONG).show();}
}
