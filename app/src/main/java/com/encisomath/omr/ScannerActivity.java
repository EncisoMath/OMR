package com.encisomath.omr;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.hardware.camera2.CameraCharacteristics;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.ComponentActivity;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.exifinterface.media.ExifInterface;

import com.google.common.util.concurrent.ListenableFuture;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Integrated CameraX OMR scanner. Camera remains inside EncisoOMR:
 * live analysis -> four corner guides -> stable detection -> automatic capture
 * -> frozen review -> Retry / Use capture.
 * This scanner detects sheet markers, not individual answer bubbles.
 * The web OMR engine evaluates bubbles on the full-resolution captured image.
 */
public final class ScannerActivity extends ComponentActivity {
    private static final int CAMERA_REQUEST = 64;
    private static final int BLUE = Color.rgb(25,117,174);
    private static final int GREEN = Color.rgb(124,179,66);
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService vision = Executors.newSingleThreadExecutor();
    private PreviewView livePreview;
    private ImageView frozenPreview;
    private GuideOverlay guides;
    private TextView message, progress;
    private LinearLayout reviewActions, liveActions;
    private Button lightButton;
    private ProcessCameraProvider provider;
    private ImageCapture capture;
    private Camera camera;
    private File capturedFile;
    private boolean paused = false, shooting = false, torch = false;
    private int stableFrames = 0, frameNumber = 0;
    private float[] previousQuad = null;
    private long lastGoodAt = 0L;

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        getWindow().setStatusBarColor(Color.rgb(10,17,25));
        getWindow().setNavigationBarColor(Color.rgb(10,17,25));
        createUi();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) beginCamera();
        else requestPermissions(new String[]{Manifest.permission.CAMERA}, CAMERA_REQUEST);
    }

    private int dp(float value) {return Math.round(value*getResources().getDisplayMetrics().density);}

    private TextView text(String s, int size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s); t.setTextSize(size); t.setTextColor(color);
        t.setGravity(Gravity.CENTER_VERTICAL);
        if (bold) t.setTypeface(null, 1);
        return t;
    }

    private Button button(String label, int color, int textColor) {
        Button b=new Button(this);
        b.setText(label); b.setAllCaps(false); b.setTextSize(14);
        b.setTextColor(textColor);
        b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(color));
        b.setMinHeight(dp(48)); b.setMinimumHeight(dp(48));
        return b;
    }

    private void createUi(){
        FrameLayout root=new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(9,16,24));
        livePreview=new PreviewView(this);
        livePreview.setImplementationMode(PreviewView.ImplementationMode.COMPATIBLE);
        livePreview.setScaleType(PreviewView.ScaleType.FIT_CENTER);
        root.addView(livePreview, new FrameLayout.LayoutParams(-1,-1));

        frozenPreview=new ImageView(this);
        frozenPreview.setBackgroundColor(Color.rgb(11,18,26));
        frozenPreview.setScaleType(ImageView.ScaleType.FIT_CENTER);
        frozenPreview.setVisibility(View.GONE);
        root.addView(frozenPreview,new FrameLayout.LayoutParams(-1,-1));

        guides=new GuideOverlay();
        root.addView(guides,new FrameLayout.LayoutParams(-1,-1));

        LinearLayout top=new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setOrientation(LinearLayout.VERTICAL);
        top.setPadding(dp(18),dp(14),dp(18),dp(14));
        top.setBackgroundColor(0xdd0b1722);

        LinearLayout line=new LinearLayout(this);
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.setGravity(Gravity.CENTER_VERTICAL);
        Button close=button("‹",Color.rgb(25,117,174),Color.WHITE);
        LinearLayout.LayoutParams backLp=new LinearLayout.LayoutParams(dp(52),dp(44));
        line.addView(close,backLp);close.setOnClickListener(v->finish());
        TextView title=text("ESCÁNER DE HOJAS",16,Color.WHITE,true);
        LinearLayout.LayoutParams titleLp=new LinearLayout.LayoutParams(0,dp(44),1);
        title.setPadding(dp(12),0,0,0);line.addView(title,titleLp);
        top.addView(line);

        message=text("Coloca la hoja completa dentro de la guía",14,Color.WHITE,true);
        message.setGravity(Gravity.CENTER);
        top.addView(message,new LinearLayout.LayoutParams(-1,dp(29)));
        progress=text("Buscando las 4 marcas de referencia…",12,0xffb4c9da,false);
        progress.setGravity(Gravity.CENTER);
        top.addView(progress,new LinearLayout.LayoutParams(-1,dp(22)));

        FrameLayout.LayoutParams topParams=new FrameLayout.LayoutParams(-1,-2,Gravity.TOP);
        root.addView(top,topParams);

        LinearLayout bottom=new LinearLayout(this);
        bottom.setOrientation(LinearLayout.VERTICAL);
        bottom.setGravity(Gravity.CENTER);
        bottom.setPadding(dp(14),dp(14),dp(14),dp(20));
        bottom.setBackgroundColor(0xe60b1722);
        FrameLayout.LayoutParams bottomParams=new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM);
        root.addView(bottom,bottomParams);

        liveActions=new LinearLayout(this);
        liveActions.setOrientation(LinearLayout.HORIZONTAL);
        liveActions.setGravity(Gravity.CENTER);
        liveActions.setPadding(0,dp(4),0,0);
        lightButton=button("Linterna",0xff334554,Color.WHITE);
        liveActions.addView(lightButton,new LinearLayout.LayoutParams(0,dp(49),1));
        lightButton.setOnClickListener(v->toggleTorch());
        Button manual=button("Capturar",BLUE,Color.WHITE);
        LinearLayout.LayoutParams manLp=new LinearLayout.LayoutParams(0,dp(49),2);
        manLp.leftMargin=dp(9);
        liveActions.addView(manual,manLp);
        manual.setOnClickListener(v->takePhoto(false));
        bottom.addView(liveActions,new LinearLayout.LayoutParams(-1,-2));

        reviewActions=new LinearLayout(this);
        reviewActions.setOrientation(LinearLayout.HORIZONTAL);
        reviewActions.setGravity(Gravity.CENTER);
        reviewActions.setVisibility(View.GONE);
        Button retry=button("Repetir",0xff334554,Color.WHITE);
        reviewActions.addView(retry,new LinearLayout.LayoutParams(0,dp(52),1));
        retry.setOnClickListener(v->retryCapture());
        Button use=button("Usar lectura",BLUE,Color.WHITE);
        LinearLayout.LayoutParams useLp=new LinearLayout.LayoutParams(0,dp(52),1.7f);
        useLp.leftMargin=dp(10);
        reviewActions.addView(use,useLp);
        use.setOnClickListener(v->accept());
        bottom.addView(reviewActions,new LinearLayout.LayoutParams(-1,-2));
        TextView hint=text("Captura automática cuando la hoja está bien alineada",11,0xffb4c9da,false);
        hint.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams hintLp=new LinearLayout.LayoutParams(-1,dp(30));
        bottom.addView(hint,hintLp);
        setContentView(root);
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode,permissions,results);
        if(requestCode!=CAMERA_REQUEST)return;
        if(results.length>0&&results[0]==PackageManager.PERMISSION_GRANTED)beginCamera();
        else{
            message.setText("Se necesita permiso de cámara");
            progress.setText("Activa el permiso para escanear sin salir de EncisoOMR");
            lightButton.setEnabled(false);
        }
    }

    private void beginCamera(){
        ListenableFuture<ProcessCameraProvider> future=ProcessCameraProvider.getInstance(this);
        future.addListener(()->{
            try{
                provider=future.get();
                provider.unbindAll();
                Preview preview=new Preview.Builder().build();
                preview.setSurfaceProvider(livePreview.getSurfaceProvider());

                capture=new ImageCapture.Builder()
                  .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                  .build();

                ImageAnalysis analysis=new ImageAnalysis.Builder()
                    .setTargetResolution(new android.util.Size(640,480))
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build();
                analysis.setAnalyzer(vision,this::inspectFrame);
                camera=provider.bindToLifecycle(this,CameraSelector.DEFAULT_BACK_CAMERA,preview,analysis,capture);
                progress.setText("Buscando las 4 marcas de referencia…");
            }catch(Exception e){
                message.setText("No se pudo activar la cámara");
                progress.setText(e.getMessage());
            }
        },ContextCompat.getMainExecutor(this));
    }

    private void inspectFrame(ImageProxy proxy){
        try{
            if(paused||shooting||++frameNumber%3!=0)return;
            MarkerResult result=detectMarkers(proxy);
            if(result==null){
                stableFrames=0;previousQuad=null;
                ui.post(()->{
                    if(paused||shooting)return;
                    guides.setMarkers(null,0,false);
                    message.setText("Coloca la hoja completa dentro de la guía");
                    progress.setText("Buscando las 4 marcas de referencia…");
                });
                return;
            }
            boolean stable=previousQuad!=null;
            if(stable) for(int i=0;i<8;i++)
                if(Math.abs(result.quad[i]-previousQuad[i])>.030f){stable=false;break;}
            stableFrames=stable?Math.min(5,stableFrames+1):1;
            previousQuad=result.quad;
            final int count=stableFrames;
            ui.post(()->{
                if(paused||shooting)return;
                guides.setMarkers(result.quad,result.aspect,true);
                message.setText(count>=4?"Hoja detectada · captura automática":"Hoja detectada · mantén el teléfono quieto");
                progress.setText("Estabilidad "+(count*25)+" %");
                if(count>=4)takePhoto(true);
            });
        }catch(Exception e){
            // A failed frame is ignored. CameraX continues providing frames.
        }finally{proxy.close();}
    }

    private static class MarkerResult {
        float[] quad;float aspect;
        MarkerResult(float[] q,float a){quad=q;aspect=a;}
    }

    private MarkerResult detectMarkers(ImageProxy image){
        final int imageW=image.getWidth(),imageH=image.getHeight();
        final int rotation=image.getImageInfo().getRotationDegrees();
        final int ow=(rotation==90||rotation==270)?imageH:imageW;
        final int oh=(rotation==90||rotation==270)?imageW:imageH;
        final int step=Math.max(2,Math.min(ow,oh)/230);
        final int width=ow/step,height=oh/step,size=width*height;
        if(size>150000)return null;
        final ByteBuffer y=image.getPlanes()[0].getBuffer();
        final int rowStride=image.getPlanes()[0].getRowStride();
        final int pixelStride=image.getPlanes()[0].getPixelStride();
        final byte[] dark=new byte[size],visited=new byte[size];
        for(int py=0;py<height;py++)for(int px=0;px<width;px++){
            int x=px*step,sy=py*step,rx,ry;
            switch(rotation){
                case 90: rx=sy;ry=imageH-1-x;break;
                case 180: rx=imageW-1-x;ry=imageH-1-sy;break;
                case 270: rx=imageW-1-sy;ry=x;break;
                default:rx=x;ry=sy;break;
            }
            int yy=y.get(ry*rowStride+rx*pixelStride)&0xff;
            dark[py*width+px]=(byte)(yy<85?1:0);
        }
        final int[] queue=new int[size];
        // Quadrants in correctly rotated portrait image: TL,TR,BL,BR.
        final float[] bx=new float[4],by=new float[4];
        final float[] quality=new float[4];
        Arrays.fill(quality,Float.NEGATIVE_INFINITY);
        for(int py=0;py<height;py++)for(int px=0;px<width;px++){
            int index=py*width+px;
            if(dark[index]==0||visited[index]!=0)continue;
            int start=0,end=1,area=0,left=px,right=px,top=py,bottom=py;
            visited[index]=1;queue[0]=index;
            while(start<end){
                int here=queue[start++],cx=here%width,cy=here/width;
                area++;
                if(cx<left)left=cx;if(cx>right)right=cx;
                if(cy<top)top=cy;if(cy>bottom)bottom=cy;
                int n;
                if(cx>0){n=here-1;if(dark[n]!=0&&visited[n]==0){visited[n]=1;queue[end++]=n;}}
                if(cx+1<width){n=here+1;if(dark[n]!=0&&visited[n]==0){visited[n]=1;queue[end++]=n;}}
                if(cy>0){n=here-width;if(dark[n]!=0&&visited[n]==0){visited[n]=1;queue[end++]=n;}}
                if(cy+1<height){n=here+width;if(dark[n]!=0&&visited[n]==0){visited[n]=1;queue[end++]=n;}}
            }
            int cw=right-left+1,ch=bottom-top+1;
            if(area<14||area>size*.007f||cw<4||ch<4)continue;
            float shape=(float)cw/ch,fill=(float)area/(cw*ch);
            if(shape<.68||shape>1.48||fill<.68)continue;
            float x=(left+right)*.5f/width,ypos=(top+bottom)*.5f/height;
            int quadrant=(ypos<.37?0:ypos>.63?2:-1);
            if(quadrant<0)continue;
            if(x<.40)quadrant+=0;else if(x>.60)quadrant+=1;else continue;
            // Prefer a marker of expected size, near the paper's four outer corners.
            float tx=(quadrant%2==0)?.15f:.85f;
            float ty=(quadrant<2)?.16f:.84f;
            float distance=(float)Math.hypot(x-tx,ypos-ty);
            float strength=(float)(Math.min(60,area)*fill)/(1+distance*7);
            if(strength>quality[quadrant]){
                quality[quadrant]=strength;bx[quadrant]=x;by[quadrant]=ypos;
            }
        }
        for(float q:quality)if(q==Float.NEGATIVE_INFINITY)return null;
        float topWidth=bx[1]-bx[0],bottomWidth=bx[3]-bx[2];
        float leftHeight=by[2]-by[0],rightHeight=by[3]-by[1];
        if(topWidth<.3||bottomWidth<.3||leftHeight<.37||rightHeight<.37)return null;
        if(Math.abs(topWidth-bottomWidth)>.17||Math.abs(leftHeight-rightHeight)>.17)return null;
        float aspect=((topWidth+bottomWidth)/2f)/((leftHeight+rightHeight)/2f);
        // Normalized image dimensions are unequal; use pixel dimensions for A4 geometry.
        float pixelAspect=aspect*(float)ow/oh;
        if(pixelAspect<.53f||pixelAspect>.90f)return null;
        return new MarkerResult(new float[]{
            bx[0],by[0], bx[1],by[1], bx[2],by[2], bx[3],by[3]
        },pixelAspect);
    }

    private void toggleTorch(){
        if(camera==null)return;
        if(!camera.getCameraInfo().hasFlashUnit()){
            progress.setText("Este teléfono no tiene linterna disponible");
            return;
        }
        torch=!torch;
        camera.getCameraControl().enableTorch(torch);
        lightButton.setText(torch?"Apagar luz":"Linterna");
    }

    private void takePhoto(boolean automatic){
        if(shooting||paused||capture==null)return;
        shooting=true;
        message.setText(automatic?"Capturando automáticamente…":"Capturando hoja…");
        progress.setText("No muevas el teléfono");
        guides.setCapturing(true);
        try{
            File output=File.createTempFile("enciso_omr_",".jpg",getCacheDir());
            ImageCapture.OutputFileOptions options=new ImageCapture.OutputFileOptions.Builder(output).build();
            capture.takePicture(options,vision,new ImageCapture.OnImageSavedCallback(){
                @Override public void onImageSaved(ImageCapture.OutputFileResults result){
                    ui.post(()->showReview(output));
                }
                @Override public void onError(ImageCaptureException exception){
                    ui.post(()->{
                        shooting=false;guides.setCapturing(false);
                        message.setText("No se pudo capturar. Inténtalo de nuevo");
                        progress.setText(exception.getMessage());
                    });
                }
            });
        }catch(Exception e){
            shooting=false;guides.setCapturing(false);progress.setText(e.getMessage());
        }
    }

    private void showReview(File file){
        paused=true;capturedFile=file;
        try{
            Bitmap photo=loadUpright(file,2100);
            if(photo==null)throw new Exception("Imagen inválida");
            // Processing works from an oriented full-quality image; JPEG transport happens
            // before the web engine's WebP storage compression.
            File oriented=File.createTempFile("enciso_review_",".jpg",getCacheDir());
            try(FileOutputStream stream=new FileOutputStream(oriented)){
                photo.compress(Bitmap.CompressFormat.JPEG,88,stream);
            }
            photo.recycle();
            file.delete();
            capturedFile=oriented;
            frozenPreview.setImageBitmap(BitmapFactory.decodeFile(oriented.getAbsolutePath()));
            frozenPreview.setVisibility(View.VISIBLE);
            livePreview.setVisibility(View.GONE);
            guides.setVisibility(View.GONE);
            liveActions.setVisibility(View.GONE);
            reviewActions.setVisibility(View.VISIBLE);
            message.setText("Fotografía capturada");
            progress.setText("Usar lectura para revisar las respuestas o repetir");
        }catch(Exception e){
            shooting=false;paused=false;guides.setCapturing(false);
            message.setText("Error preparando fotografía");
            progress.setText(e.getMessage());
        }
    }

    private static Bitmap loadUpright(File file,int maxDimension)throws Exception{
        BitmapFactory.Options o=new BitmapFactory.Options();
        o.inJustDecodeBounds=true;
        BitmapFactory.decodeFile(file.getAbsolutePath(),o);
        if(o.outWidth<=0||o.outHeight<=0)return null;
        int sample=1;while(Math.max(o.outWidth,o.outHeight)/sample>maxDimension)sample*=2;
        o.inJustDecodeBounds=false;o.inSampleSize=sample;
        Bitmap source=BitmapFactory.decodeFile(file.getAbsolutePath(),o);
        if(source==null)return null;
        ExifInterface exif=new ExifInterface(file.getAbsolutePath());
        int orientation=exif.getAttributeInt(ExifInterface.TAG_ORIENTATION,ExifInterface.ORIENTATION_NORMAL);
        Matrix mat=new Matrix();
        if(orientation==ExifInterface.ORIENTATION_ROTATE_90)mat.postRotate(90);
        else if(orientation==ExifInterface.ORIENTATION_ROTATE_180)mat.postRotate(180);
        else if(orientation==ExifInterface.ORIENTATION_ROTATE_270)mat.postRotate(270);
        if(mat.isIdentity())return source;
        Bitmap rotated=Bitmap.createBitmap(source,0,0,source.getWidth(),source.getHeight(),mat,true);
        if(rotated!=source)source.recycle();
        return rotated;
    }

    private void retryCapture(){
        paused=false;shooting=false;stableFrames=0;previousQuad=null;
        if(capturedFile!=null)capturedFile.delete();
        capturedFile=null;
        frozenPreview.setVisibility(View.GONE);
        frozenPreview.setImageDrawable(null);
        livePreview.setVisibility(View.VISIBLE);
        guides.setVisibility(View.VISIBLE);
        guides.setCapturing(false);
        guides.setMarkers(null,0,false);
        liveActions.setVisibility(View.VISIBLE);
        reviewActions.setVisibility(View.GONE);
        message.setText("Coloca la hoja completa dentro de la guía");
        progress.setText("Buscando las 4 marcas de referencia…");
    }

    private void accept(){
        if(capturedFile==null||!capturedFile.isFile())return;
        Intent result=new Intent();
        result.putExtra("scan_file",capturedFile.getAbsolutePath());
        setResult(Activity.RESULT_OK,result);
        finish();
    }

    @Override protected void onDestroy(){
        vision.shutdownNow();
        if(provider!=null)provider.unbindAll();
        if(isFinishing()&&capturedFile!=null&&getIntent()!=null&&
                !getIntent().getBooleanExtra("keep_file",false)){
            // The file is consumed by MainActivity after return. Do not delete here.
        }
        super.onDestroy();
    }

    private final class GuideOverlay extends View{
        private final Paint paint=new Paint(3);
        private float[] detected=null;private boolean isVisible=false,isCapturing=false;
        private float aspect=.707f;
        GuideOverlay(){super(ScannerActivity.this);setLayerType(View.LAYER_TYPE_SOFTWARE,null);}
        void setMarkers(float[] points,float ar,boolean visible){
            detected=points==null?null:points.clone();isVisible=visible;
            if(ar>0)aspect=ar;postInvalidate();
        }
        void setCapturing(boolean yes){isCapturing=yes;postInvalidate();}
        @Override protected void onDraw(Canvas c){
            super.onDraw(c);
            float width=getWidth(),height=getHeight();
            float cardH=Math.min(height*.65f,width*.85f/0.707f);
            float cardW=cardH*.707f,left=(width-cardW)/2,top=(height-cardH)/2;
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(3));
            paint.setColor(isCapturing?GREEN:isVisible?GREEN:0xffe0ebf4);
            paint.setStrokeCap(Paint.Cap.ROUND);
            float cut=Math.min(dp(29),cardW*.15f);
            for(int xx=0;xx<2;xx++)for(int yy=0;yy<2;yy++){
                float ax=left+(xx==0?0:cardW),ay=top+(yy==0?0:cardH);
                c.drawLine(ax,ay,ax+(xx==0?cut:-cut),ay,paint);
                c.drawLine(ax,ay,ax,ay+(yy==0?cut:-cut),paint);
            }
            if(detected!=null&&isVisible){
                float orientedWidth=Math.min(width,height*.75f),orientedHeight=orientedWidth/.75f;
                if(orientedHeight>height){orientedHeight=height;orientedWidth=height*.75f;}
                float dx=(width-orientedWidth)/2,dy=(height-orientedHeight)/2;
                float[] pts=detected;
                Path quad=new Path();
                quad.moveTo(dx+pts[0]*orientedWidth,dy+pts[1]*orientedHeight);
                quad.lineTo(dx+pts[2]*orientedWidth,dy+pts[3]*orientedHeight);
                quad.lineTo(dx+pts[6]*orientedWidth,dy+pts[7]*orientedHeight);
                quad.lineTo(dx+pts[4]*orientedWidth,dy+pts[5]*orientedHeight);
                quad.close();
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(0x247cb342);c.drawPath(quad,paint);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(dp(2));
                paint.setColor(GREEN);
                c.drawPath(quad,paint);
                paint.setStyle(Paint.Style.FILL);
                for(int i=0;i<8;i+=2){
                    c.drawCircle(dx+pts[i]*orientedWidth,dy+pts[i+1]*orientedHeight,dp(5),paint);
                }
            }
        }
    }
}
