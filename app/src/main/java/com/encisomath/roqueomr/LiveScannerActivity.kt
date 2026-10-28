package com.encisomath.roqueomr

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.SystemClock
import android.util.Size
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min

/**
 * Live CameraX OMR capture.
 *
 * 1. CameraX analyzes a live RGBA image every ~450ms.
 * 2. OmrEngine identifies four square fiducials and locates all bubbles.
 * 3. Three geometrically stable detections trigger an automatic freeze.
 * 4. The frozen photo shows the actual marked options and guide positions.
 *
 * Results are never saved without the teacher pressing "Usar lectura".
 */
class LiveScannerActivity : ComponentActivity() {
 private val ink=Color.parseColor("#0E1A29")
 private val mint=Color.parseColor("#39DAB3")
 private val gold=Color.parseColor("#FFC76A")
 private val worker:ExecutorService=Executors.newSingleThreadExecutor()
 private lateinit var preview:PreviewView
 private lateinit var photo:ImageView
 private lateinit var overlay:OmrOverlay
 private lateinit var topStatus:TextView
 private lateinit var status:TextView
 private lateinit var accept:Button
 private lateinit var repeat:Button
 private lateinit var exam:Exam
 private var provider:ProcessCameraProvider?=null
 private var accepted:OmrEngine.DetailedScan?=null
 @Volatile private var frozen=false
 private var stable=0
 private var previous:List<OmrEngine.Pixel>?=null
 private var lastProcessed=0L
 private val requiredStableFrames=3

 override fun onCreate(savedInstanceState:Bundle?) {
  super.onCreate(savedInstanceState)
  val json=intent.getStringExtra("exam") ?: run { finish();return }
  exam=try { Exam.fromJson(org.json.JSONObject(json)) }
       catch(_:Exception) { finish();return }
  window.statusBarColor=ink
  window.navigationBarColor=ink
  window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
  composeUi()
  if(ContextCompat.checkSelfPermission(this,Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED)
   startCamera()
  else ActivityCompat.requestPermissions(this,arrayOf(Manifest.permission.CAMERA),42)
 }

 private fun rect(color:Int):GradientDrawable = GradientDrawable().apply {
  setColor(color);cornerRadius=resources.displayMetrics.density*4
 }
 private fun dp(n:Int)=(n*resources.displayMetrics.density).toInt()
 private fun text(value:String,size:Float=14f,bold:Boolean=false)=TextView(this).apply {
  text=value;textSize=size;setTextColor(Color.WHITE)
  if(bold)typeface=Typeface.DEFAULT_BOLD
 }
 private fun action(value:String,color:Int,foreground:Int,onClick:()->Unit)=Button(this).apply {
  text=value;isAllCaps=false;textSize=14f
  setTextColor(foreground);background=rect(color)
  setOnClickListener { onClick() }
 }

 private fun composeUi() {
  val frame=FrameLayout(this).apply { setBackgroundColor(ink) }
  preview=PreviewView(this).apply {
   implementationMode=PreviewView.ImplementationMode.COMPATIBLE
   scaleType=PreviewView.ScaleType.FIT_CENTER
  }
  frame.addView(preview,FrameLayout.LayoutParams(-1,-1))
  photo=ImageView(this).apply {
   scaleType=ImageView.ScaleType.FIT_CENTER
   visibility=View.GONE
  }
  frame.addView(photo,FrameLayout.LayoutParams(-1,-1))
  overlay=OmrOverlay(this)
  frame.addView(overlay,FrameLayout.LayoutParams(-1,-1))

  val header=LinearLayout(this).apply {
   orientation=LinearLayout.VERTICAL
   setPadding(dp(20),dp(16),dp(20),dp(16))
   background=rect(Color.parseColor("#E5142436"))
  }
  header.addView(text("ROQUE OMR  /  CÁMARA",19f,true))
  topStatus=text("Buscando las cuatro guías negras…",12.5f)
  topStatus.setTextColor(Color.parseColor("#C8E7E4"))
  header.addView(topStatus)
  frame.addView(header,FrameLayout.LayoutParams(-1,-2,Gravity.TOP).apply {
   setMargins(dp(12),dp(12),dp(12),0)
  })

  val bottom=LinearLayout(this).apply {
   orientation=LinearLayout.VERTICAL;setPadding(dp(16),dp(14),dp(16),dp(15))
   background=rect(Color.parseColor("#EE142436"))
  }
  status=text("Mantén la hoja completa dentro de la cámara.",13f)
  bottom.addView(status)
  val bar=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL }
  val cancel=action("Cancelar",Color.parseColor("#324559"),Color.WHITE) { finish() }
  bar.addView(cancel,LinearLayout.LayoutParams(0,dp(49),1f).apply { rightMargin=dp(6) })
  repeat=action("Repetir",Color.parseColor("#324559"),Color.WHITE) { reset() }.apply {
   visibility=View.GONE
  }
  bar.addView(repeat,LinearLayout.LayoutParams(0,dp(49),1f).apply { rightMargin=dp(6) })
  accept=action("Usar lectura",mint,ink) { confirm() }.apply { visibility=View.GONE }
  bar.addView(accept,LinearLayout.LayoutParams(0,dp(49),1.1f))
  bottom.addView(bar,LinearLayout.LayoutParams(-1,dp(49)).apply { topMargin=dp(13) })
  frame.addView(bottom,FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM).apply {
   setMargins(dp(12),0,dp(12),dp(14))
  })
  setContentView(frame)
 }

 override fun onRequestPermissionsResult(requestCode:Int,permissions:Array<String>,grantResults:IntArray) {
  super.onRequestPermissionsResult(requestCode,permissions,grantResults)
  if(requestCode==42) {
   if(grantResults.isNotEmpty()&&grantResults[0]==PackageManager.PERMISSION_GRANTED)startCamera()
   else {
    showPermissionExplanation()
   }
  }
 }

 // Permission rejection leaves the user a clear explanation rather than a black camera view.
 private fun showPermissionExplanation() {
  android.app.AlertDialog.Builder(this).setTitle("Permiso de cámara")
   .setMessage("Roque OMR necesita acceso a la cámara para detectar la hoja automáticamente.")
   .setPositiveButton("Cerrar") { _,_ -> finish() }.setCancelable(false).show()
 }

 private fun startCamera() {
  val future=ProcessCameraProvider.getInstance(this)
  future.addListener({
   try {
    val cameraProvider=future.get()
    provider=cameraProvider
    val previewCase=Preview.Builder().build().apply {
     setSurfaceProvider(preview.surfaceProvider)
    }
    val analysis=ImageAnalysis.Builder()
     .setTargetResolution(Size(1280,960))
     .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
     .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
     .build()
    analysis.setAnalyzer(worker) { image -> analyzeFrame(image) }
    cameraProvider.unbindAll()
    cameraProvider.bindToLifecycle(this,CameraSelector.DEFAULT_BACK_CAMERA,previewCase,analysis)
   } catch(e:Exception) {
    topStatus.text="No se pudo iniciar la cámara"
    status.text=e.message ?: "Revisa la cámara del dispositivo."
   }
  },ContextCompat.getMainExecutor(this))
 }

 private fun analyzeFrame(frame:ImageProxy) {
  try {
   if(frozen)return
   val now=SystemClock.elapsedRealtime()
   if(now-lastProcessed<450L)return
   lastProcessed=now
   val image=frame.toBitmap()
   val angle=frame.imageInfo.rotationDegrees
   val bitmap=if(angle==0)image else {
    val matrix=Matrix().apply { postRotate(angle.toFloat()) }
    Bitmap.createBitmap(image,0,0,image.width,image.height,matrix,true)
   }
   if(frozen)return
   try {
    val detection=OmrEngine.scanDetailed(bitmap,exam)
    val corners=detection.corners
    val old=previous
    val close=old!=null&&old.size==4&&corners.indices.all { i ->
     hypot(corners[i].x-old[i].x,corners[i].y-old[i].y)<bitmap.width*0.027
    }
    stable=if(close)stable+1 else 1
    previous=corners
    val level=stable
    runOnUiThread {
     if(frozen)return@runOnUiThread
     overlay.setLive(detection)
     topStatus.text=if(level<requiredStableFrames)
      "Guías detectadas · estabilizando $level/$requiredStableFrames"
      else "Hoja alineada"
     status.text="No muevas el teléfono. Captura automática activada."
    }
    if(stable>=requiredStableFrames) {
     frozen=true
     runOnUiThread { freezePicture(bitmap,detection) }
    }
   } catch(_:OmrEngine.ScanException) {
    stable=0;previous=null
    runOnUiThread {
     if(!frozen) {
      overlay.setLive(null)
      topStatus.text="Buscando las cuatro guías negras…"
      status.text="Coloca la hoja completa, con buena luz y sin recortes."
     }
    }
   }
  } catch(e:Exception) {
   runOnUiThread {
    if(!frozen)status.text="Ajusta la iluminación y vuelve a enfocar la hoja."
   }
  } finally {
   frame.close()
  }
 }

 private fun freezePicture(bitmap:Bitmap,detected:OmrEngine.DetailedScan) {
  accepted=detected
  photo.setImageBitmap(bitmap)
  photo.visibility=View.VISIBLE
  overlay.setReview(detected)
  val result=detected.result
  topStatus.text="CAPTURA AUTOMÁTICA COMPLETADA"
  status.text="Marcadas: "+result.answers.count { it in 'A'..'D' }+
   "  ·  Dudosas: "+result.uncertain.size+
   "  ·  Vacías: "+result.answers.count { it=='-' }
  repeat.visibility=View.VISIBLE
  accept.visibility=View.VISIBLE
 }

 private fun confirm() {
  val scan=accepted ?:return
  val result=Intent().apply {
   putExtra("answers",scan.result.answers.joinToString(""))
   putExtra("uncertain",scan.result.uncertain.toIntArray())
   putExtra("examId",exam.id)
  }
  setResult(RESULT_OK,result)
  finish()
 }
 private fun reset() {
  accepted=null;frozen=false;stable=0;previous=null;lastProcessed=0L
  photo.setImageDrawable(null);photo.visibility=View.GONE
  repeat.visibility=View.GONE;accept.visibility=View.GONE
  overlay.setLive(null)
  topStatus.text="Buscando las cuatro guías negras…"
  status.text="Mantén la hoja completa dentro de la cámara."
 }
 override fun onDestroy() {
  provider?.unbindAll()
  worker.shutdown()
  window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
  super.onDestroy()
 }
}

/** Transparent overlay, geometrically registered to the FIT_CENTER image. */
private class OmrOverlay(context:android.content.Context):View(context) {
 private var scan:OmrEngine.DetailedScan?=null
 private var review=false
 private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
 private val mint=Color.parseColor("#39DAB3")
 private val gold=Color.parseColor("#FFC76A")
 fun setLive(value:OmrEngine.DetailedScan?) {scan=value;review=false;invalidate()}
 fun setReview(value:OmrEngine.DetailedScan) {scan=value;review=true;invalidate()}

 override fun onDraw(canvas:Canvas) {
  super.onDraw(canvas)
  val d=scan
  if(d==null) {
   val margin=min(width,height)*0.15f
   val x1=margin; val x2=width-margin
   val y1=height*0.17f;val y2=height*0.83f
   paint.style=Paint.Style.STROKE;paint.strokeWidth=4f
   paint.color=Color.parseColor("#D0FFFFFF")
   for ((x,y,dx,dy) in listOf(
    arrayOf(x1,y1,1f,1f),arrayOf(x2,y1,-1f,1f),
    arrayOf(x1,y2,1f,-1f),arrayOf(x2,y2,-1f,-1f))) {
    canvas.drawLine(x,y,x+dx*32f,y,paint)
    canvas.drawLine(x,y,x,y+dy*32f,paint)
   }
   return
  }
  val w=d.imageWidth.toFloat();val h=d.imageHeight.toFloat()
  val scale=min(width/w,height/h)
  val offsetX=(width-w*scale)/2f;val offsetY=(height-h*scale)/2f
  fun px(point:OmrEngine.Pixel):Float=offsetX+point.x.toFloat()*scale
  fun py(point:OmrEngine.Pixel):Float=offsetY+point.y.toFloat()*scale
  val c=d.corners
  if(c.size==4) {
   val perimeter=listOf(c[0],c[1],c[3],c[2])
   paint.style=Paint.Style.STROKE
   paint.strokeWidth=3.5f;paint.color=mint
   val path=Path()
   path.moveTo(px(perimeter[0]),py(perimeter[0]))
   perimeter.drop(1).forEach { path.lineTo(px(it),py(it)) }
   path.close()
   canvas.drawPath(path,paint)
   c.forEach {
    paint.style=Paint.Style.FILL;paint.color=mint
    canvas.drawCircle(px(it),py(it),8f,paint)
    paint.style=Paint.Style.STROKE;paint.strokeWidth=3f
    canvas.drawCircle(px(it),py(it),14f,paint)
   }
  }
  if(!review)return
  val result=d.result
  result.answers.forEachIndexed { q,value ->
   val bubble=d.bubbleCenters.getOrNull(q) ?:return@forEachIndexed
   if(value in 'A'..'D') {
    val i=value-'A'
    if(i>=bubble.size)return@forEachIndexed
    val pt=bubble[i]
    paint.style=Paint.Style.STROKE;paint.strokeWidth=3.0f
    paint.color=mint
    canvas.drawCircle(px(pt),py(pt),maxOf(7f,6f*scale),paint)
    paint.style=Paint.Style.FILL
    paint.color=Color.parseColor("#B739DAB3")
    canvas.drawCircle(px(pt),py(pt),maxOf(4f,4.2f*scale),paint)
   } else if(value=='?') {
    val scores=result.fillScores.getOrNull(q) ?:emptyList()
    scores.forEachIndexed { index,score ->
     if(score>=0.12 && index<bubble.size) {
      paint.style=Paint.Style.STROKE;paint.strokeWidth=3f
      paint.color=gold
      canvas.drawCircle(px(bubble[index]),py(bubble[index]),maxOf(8f,6f*scale),paint)
     }
    }
   }
  }
 }
}
