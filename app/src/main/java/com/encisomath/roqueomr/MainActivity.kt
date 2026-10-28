package com.encisomath.roqueomr

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.text.InputType
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import kotlin.math.roundToInt

class MainActivity:Activity() {
 private val bg=Color.rgb(16,26,41)
 private val panel=Color.rgb(27,41,59)
 private val accent=Color.rgb(65,217,181)
 private val white=Color.rgb(247,250,252)
 private val muted=Color.rgb(167,182,200)
 private val prefs by lazy { getSharedPreferences("roqueomr",MODE_PRIVATE) }
 private val exams=mutableListOf<Exam>()
 private var selectedId="demo-40"
 private var section=0
 private var lastScan:ScanResult?=null
 private var scannedExam:Exam?=null
 private var studentId=""
 private var cameraUri:Uri?=null
 private var studentField:EditText?=null
 private lateinit var body:LinearLayout
 private fun px(n:Int)=(n*resources.displayMetrics.density).roundToInt()
 private fun background(c:Int)=GradientDrawable().apply { setColor(c);cornerRadius=px(4).toFloat() }
 private fun label(s:String,size:Float=14f,color:Int=white,bold:Boolean=false)=TextView(this).apply {
  text=s;textSize=size;setTextColor(color);if(bold)typeface=Typeface.DEFAULT_BOLD
  setLineSpacing(px(3).toFloat(),1f)
 }
 private fun gap(n:Int=10)=View(this).apply { layoutParams=LinearLayout.LayoutParams(1,px(n)) }
 private fun card()=LinearLayout(this).apply {
  orientation=LinearLayout.VERTICAL;setPadding(px(17),px(17),px(17),px(17))
  background=background(panel)
  layoutParams=LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=px(13) }
 }
 private fun btn(s:String,primary:Boolean=true,onClick:()->Unit)=Button(this).apply {
  text=s;textSize=13f;isAllCaps=false
  setTextColor(if(primary)bg else white)
  background=background(if(primary)accent else Color.rgb(45,60,79))
  setOnClickListener { onClick() }
 }
 private fun addButton(parent:LinearLayout,s:String,primary:Boolean=true,onClick:()->Unit) {
  parent.addView(btn(s,primary,onClick),LinearLayout.LayoutParams(-1,px(50)).apply { topMargin=px(9) })
 }
 private fun input(value:String,hint:String,numeric:Boolean=false)=EditText(this).apply {
  setText(value);setHint(hint);setTextColor(white);setHintTextColor(muted);textSize=15f
  setPadding(px(12),px(11),px(12),px(11));background=background(bg)
  inputType=if(numeric)InputType.TYPE_CLASS_NUMBER else InputType.TYPE_CLASS_TEXT
 }

 override fun onCreate(savedInstanceState:Bundle?) {
  super.onCreate(savedInstanceState)
  window.statusBarColor=bg;window.navigationBarColor=bg
  loadExams();render()
 }
 private fun loadExams() {
  try {
   val j=JSONArray(prefs.getString("exams","[]"))
   for(i in 0 until j.length())exams.add(Exam.fromJson(j.getJSONObject(i)))
  } catch(_:Exception) {}
  if(exams.isEmpty())exams.add(Exam.demo())
  selectedId=prefs.getString("selected",exams.first().id) ?: exams.first().id
  if(exams.none { it.id==selectedId })selectedId=exams.first().id
  saveExams()
 }
 private fun saveExams() {
  val a=JSONArray();exams.forEach { a.put(it.toJson()) }
  prefs.edit().putString("exams",a.toString()).putString("selected",selectedId).apply()
 }
 private fun current()=exams.firstOrNull { it.id==selectedId } ?: exams.first()

 private fun render() {
  val root=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL;setBackgroundColor(bg) }
  val header=LinearLayout(this).apply {
   orientation=LinearLayout.VERTICAL;setPadding(px(20),px(19),px(20),px(15))
  }
  header.addView(label("ROQUE OMR",26f,white,true))
  header.addView(label("EVALUACIÓN ÓPTICA  ·  OFFLINE",10f,accent,true))
  root.addView(header)
  val tabs=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL;setPadding(px(14),0,px(14),px(12)) }
  listOf("PRUEBAS","LEER","RESULTADOS").forEachIndexed { i,name ->
   val b=btn(name,i==section) { section=i;render() }
   b.textSize=11.5f
   tabs.addView(b,LinearLayout.LayoutParams(0,px(43),1f).apply {
    leftMargin=px(3);rightMargin=px(3)
   })
  }
  root.addView(tabs)
  val scroll=ScrollView(this)
  body=LinearLayout(this).apply {
   orientation=LinearLayout.VERTICAL;setPadding(px(18),px(8),px(18),px(26))
  }
  scroll.addView(body)
  root.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
  setContentView(root)
  when(section){0->examsScreen();1->scanScreen();else->resultsScreen()}
 }
 private fun heading(title:String,subtitle:String) {
  body.addView(label(title,20f,white,true));body.addView(gap(5))
  body.addView(label(subtitle,13f,muted));body.addView(gap(15))
 }
 private fun examsScreen() {
  heading("Mis pruebas","Diseña las hojas y administra las respuestas.")
  val e=current()
  val info=card()
  info.addView(label("PRUEBA ACTIVA",11f,accent,true));info.addView(gap(6))
  info.addView(label(e.title,20f,white,true));info.addView(gap(9))
  info.addView(label(e.count.toString()+" preguntas  ·  "+e.choices+" opciones  ·  A4",13f,muted))
  info.addView(label(if(e.key.length==e.count)"Clave completa ✓" else "Clave pendiente",12f,accent))
  addButton(info,"Seleccionar otra prueba",false) { pickExam() }
  body.addView(info)
  val actions=card()
  actions.addView(label("CREAR Y PREPARAR",14f,white,true))
  addButton(actions,"+ Nueva prueba") { newExam() }
  addButton(actions,"Editar clave de respuestas",false) { editKey() }
  addButton(actions,"Generar hoja PDF para imprimir") { makePdf() }
  body.addView(actions)
  val tips=card()
  tips.addView(label("FORMATO A4",11f,accent,true));tips.addView(gap())
  tips.addView(label("Imprime a escala 100 %, sin recortar márgenes. Las cuatro marcas negras deben quedar visibles. Esta versión lee hojas generadas por Roque OMR.",13f,muted))
  body.addView(tips)
 }
 private fun pickExam() {
  AlertDialog.Builder(this).setTitle("Elegir prueba")
   .setItems(exams.map { it.title }.toTypedArray()){_,i->
    selectedId=exams[i].id;saveExams();lastScan=null;scannedExam=null;render()
   }.setNegativeButton("Cancelar",null).show()
 }
 private fun newExam() {
  val layout=LinearLayout(this).apply {
   orientation=LinearLayout.VERTICAL;setPadding(px(20),px(8),px(20),0)
  }
  val name=input("","Nombre de la prueba")
  val count=input("40","Número de preguntas",true)
  val choices=input("4","Opciones: 2, 3 o 4",true)
  layout.addView(name);layout.addView(gap());layout.addView(count)
  layout.addView(gap());layout.addView(choices)
  AlertDialog.Builder(this).setTitle("Nueva prueba").setView(layout)
   .setPositiveButton("Crear"){_,_->
    val n=count.text.toString().toIntOrNull();val k=choices.text.toString().toIntOrNull()
    val title=name.text.toString().trim()
    if(title.isBlank()||n==null||n !in 1..100||k==null||k !in 2..4)
     toast("Indica nombre, 1-100 preguntas y 2-4 opciones.")
    else {
     val e=Exam(UUID.randomUUID().toString(),title,n,k,"")
     exams.add(e);selectedId=e.id;saveExams();lastScan=null;scannedExam=null;render()
    }
   }.setNegativeButton("Cancelar",null).show()
 }
 private fun editKey() {
  val e=current()
  val field=EditText(this).apply {
   setText(e.key);setSelection(text.length)
   hint="ABCDABCD..."
   inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS or InputType.TYPE_TEXT_FLAG_MULTI_LINE
   setPadding(px(18),px(12),px(18),px(12))
  }
  AlertDialog.Builder(this).setTitle("Clave · "+e.count+" preguntas")
   .setMessage("Escribe todas las letras seguidas, de A hasta "+('A'+e.choices-1)+".")
   .setView(field).setPositiveButton("Guardar"){_,_->
    val key=field.text.toString().uppercase().filterNot { it.isWhitespace()||it==','||it==';' }
    if(key.isNotBlank()&&(key.length!=e.count||key.any { it !in 'A'..('A'+e.choices-1) }))
     toast("La clave debe tener "+e.count+" letras válidas.")
    else {
     val index=exams.indexOfFirst { it.id==e.id }
     if(index>=0)exams[index]=e.copy(key=key)
     saveExams();render()
    }
   }.setNegativeButton("Cancelar",null).show()
 }
 private fun makePdf() {
  val intent=Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
   addCategory(Intent.CATEGORY_OPENABLE);type="application/pdf"
   putExtra(Intent.EXTRA_TITLE,"RoqueOMR_"+current().count+"_preguntas.pdf")
  }
  startActivityForResult(intent,10)
 }
 private fun scanScreen() {
  heading("Leer respuestas","Escanea automáticamente con la cámara integrada.")
  val card=card()
  card.addView(label("PRUEBA SELECCIONADA",11f,accent,true))
  card.addView(label(current().title,18f,white,true));card.addView(gap(12))
  card.addView(label("ID DEL ESTUDIANTE",11f,muted,true));card.addView(gap(5))
  studentField=input(prefs.getString("student","") ?: "","Identificador manual")
  card.addView(studentField);body.addView(card)
  val actions=card()
  actions.addView(label("CAPTURAR HOJA",14f,white,true))
  addButton(actions,"Escanear automáticamente"){rememberId();liveScanner()}
  addButton(actions,"Importar imagen o PDF",false){rememberId();importFile()}
  body.addView(actions)
  val tips=card()
  tips.addView(label("ANTES DE LEER",11f,accent,true))
  tips.addView(gap())
  tips.addView(label("La cámara detecta las cuatro guías, espera estabilidad y se autocaptura. Después verás sobre la foto las respuestas reconocidas. Revisa las marcas dudosas antes de guardar.",13f,muted))
  body.addView(tips)
 }
 private fun rememberId() {
  studentId=studentField?.text?.toString()?.trim() ?: ""
  prefs.edit().putString("student",studentId).apply()
 }
 private fun liveScanner() {
  val intent=Intent(this,LiveScannerActivity::class.java)
  intent.putExtra("exam",current().toJson().toString())
  startActivityForResult(intent,14)
 }
 private fun acceptLiveScan(data:Intent?) {
  val exam=current()
  if(data?.getStringExtra("examId")!=exam.id) {
   toast("La hoja corresponde a otra prueba.");return
  }
  val marks=data.getStringExtra("answers") ?: return
  if(marks.length!=exam.count) { toast("Lectura incompleta");return }
  val uncertain=data.getIntArrayExtra("uncertain")?.toMutableSet() ?: mutableSetOf()
  lastScan=ScanResult(marks.toMutableList(),uncertain,emptyList(),4)
  scannedExam=exam
  section=2
  render()
 }
 private fun camera() {
  try {
   val f=File.createTempFile("roque_",".jpg",cacheDir)
   cameraUri=FileProvider.getUriForFile(this,"com.encisomath.roqueomr.files",f)
   val intent=Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
    putExtra(MediaStore.EXTRA_OUTPUT,cameraUri)
    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
   }
   startActivityForResult(intent,11)
  } catch(e:Exception){error("Cámara no disponible",e)}
 }
 private fun importFile() {
  val intent=Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
   addCategory(Intent.CATEGORY_OPENABLE);type="*/*"
   putExtra(Intent.EXTRA_MIME_TYPES,arrayOf("image/*","application/pdf"))
  }
  startActivityForResult(intent,12)
 }
 @Deprecated("Uses Android document and camera activities")
 override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?) {
  super.onActivityResult(requestCode,resultCode,data)
  if(resultCode!=RESULT_OK)return
  when(requestCode) {
   14 -> acceptLiveScan(data)
   10 -> {
    val uri=data?.data ?: return
    try {
     contentResolver.openOutputStream(uri)?.use { SheetPdf.write(current(),it) }
      ?: throw Exception("No se pudo escribir el documento.")
     toast("Hoja PDF guardada")
    } catch(e:Exception){error("Error al crear PDF",e)}
   }
   11 -> cameraUri?.let { analyze(it) }
   12 -> data?.data?.let { analyze(it) }
   13 -> data?.data?.let { saveCsv(it) }
  }
 }
 private fun readImage(uri:Uri):Bitmap {
  val mime=contentResolver.getType(uri) ?: ""
  if(mime=="application/pdf") {
   val descriptor=contentResolver.openFileDescriptor(uri,"r") ?: throw Exception("PDF ilegible")
   descriptor.use { fd ->
    PdfRenderer(fd).use { renderer ->
     renderer.openPage(0).use { page ->
      val bitmap=Bitmap.createBitmap(page.width*2,page.height*2,Bitmap.Config.ARGB_8888)
      bitmap.eraseColor(Color.WHITE)
      page.render(bitmap,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
      return bitmap
     }
    }
   }
  }
  val options=BitmapFactory.Options().apply { inJustDecodeBounds=true }
  contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it,null,options) }
  options.inSampleSize=1
  while(options.outWidth/options.inSampleSize>2400 || options.outHeight/options.inSampleSize>2400)
   options.inSampleSize*=2
  options.inJustDecodeBounds=false
  val bitmap=contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it,null,options) }
   ?: throw Exception("Imagen no compatible.")
  val angle=try {
   val o=contentResolver.openInputStream(uri)?.use {
    ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION,ExifInterface.ORIENTATION_NORMAL)
   }
   when(o) {
    ExifInterface.ORIENTATION_ROTATE_90->90f
    ExifInterface.ORIENTATION_ROTATE_180->180f
    ExifInterface.ORIENTATION_ROTATE_270->270f
    else->0f
   }
  } catch(_:Exception){0f}
  if(angle==0f)return bitmap
  val matrix=android.graphics.Matrix().apply { postRotate(angle) }
  return Bitmap.createBitmap(bitmap,0,0,bitmap.width,bitmap.height,matrix,true)
 }
 private fun analyze(uri:Uri) {
  val exam=current()
  val loading=AlertDialog.Builder(this).setTitle("Analizando hoja")
   .setMessage("Calibrando y leyendo marcas...").setCancelable(false).create()
  loading.show()
  Thread {
   try {
    val scan=OmrEngine.scan(readImage(uri),exam)
    runOnUiThread {
     loading.dismiss();lastScan=scan;scannedExam=exam;section=2;render()
    }
   } catch(e:Exception) {
    runOnUiThread { loading.dismiss();error("No se pudo leer la hoja",e) }
   }
  }.start()
 }
 private fun resultsScreen() {
  heading("Resultados","Revisa cada lectura antes de guardarla.")
  val scan=lastScan;val exam=scannedExam
  if(scan!=null&&exam!=null) {
   val summary=card()
   summary.addView(label("ÚLTIMA LECTURA",11f,accent,true))
   summary.addView(label(exam.title,18f,white,true))
   summary.addView(label("Estudiante: "+studentId.ifBlank { "SIN ID" },13f,muted))
   summary.addView(gap())
   if(exam.key.length==exam.count) {
    summary.addView(label(scan.correct(exam).toString()+" / "+exam.count,36f,accent,true))
    summary.addView(label("Porcentaje de aciertos: "+("%.1f".format(100.0*scan.correct(exam)/exam.count))+" %",13f,white))
   } else summary.addView(label("Falta la clave para calificar",15f,muted,true))
   summary.addView(label("Marcas dudosas: "+scan.uncertain.size+"  ·  Vacías: "+scan.answers.count { it=='-' },12f,muted))
   addButton(summary,"Guardar calificación"){saveResult()}
   body.addView(summary)
   val answers=card()
   answers.addView(label("RESPUESTAS DETECTADAS",14f,white,true))
   answers.addView(label("Pulsa una fila para modificarla.",12f,muted))
   answers.addView(gap(10))
   scan.answers.forEachIndexed { index,answer ->
    val key=exam.key.getOrNull(index)?.toString() ?: "—"
    val value=if(answer=='-')"—" else answer.toString()
    val row=label("%02d    %s    /    Clave %s".format(index+1,value,key),
     14f,if(index in scan.uncertain)Color.rgb(255,201,117) else white)
    row.setPadding(px(8),px(10),px(8),px(10));row.background=background(bg)
    row.setOnClickListener { correct(index) }
    answers.addView(row,LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=px(4) })
   }
   body.addView(answers)
  } else {
   val empty=card()
   empty.addView(label("Todavía no hay lecturas.",15f,white,true))
   addButton(empty,"Escanear una hoja"){section=1;render()}
   body.addView(empty)
  }
  val history=card()
  history.addView(label("HISTORIAL EN ESTE TELÉFONO",14f,white,true))
  history.addView(gap())
  val arr=history()
  if(arr.length()==0)history.addView(label("No hay resultados guardados.",13f,muted))
  else {
   for(i in arr.length()-1 downTo 0) {
    val j=arr.optJSONObject(i) ?: continue
    history.addView(label(j.optString("student")+" · "+j.optString("exam")+
     " · "+j.optInt("correct")+"/"+j.optInt("total"),12f,muted))
    history.addView(gap(8))
   }
   addButton(history,"Exportar CSV",false) {
    val intent=Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
     addCategory(Intent.CATEGORY_OPENABLE);type="text/csv"
     putExtra(Intent.EXTRA_TITLE,"RoqueOMR_resultados.csv")
    }
    startActivityForResult(intent,13)
   }
  }
  body.addView(history)
 }
 private fun correct(index:Int) {
  val scan=lastScan ?: return
  val exam=scannedExam ?: return
  val options=(0 until exam.choices).map { ('A'+it).toString() }+listOf("En blanco")
  AlertDialog.Builder(this).setTitle("Pregunta "+(index+1))
   .setItems(options.toTypedArray()){_,choice->
    scan.answers[index]=if(choice==exam.choices)'-' else 'A'+choice
    scan.uncertain.remove(index);render()
   }.setNegativeButton("Cancelar",null).show()
 }
 private fun history()=try { JSONArray(prefs.getString("history","[]")) } catch(_:Exception){JSONArray()}
 private fun saveResult() {
  val scan=lastScan ?: return
  val exam=scannedExam ?: return
  if(exam.key.length!=exam.count) { toast("Configura antes la clave completa.");return }
  if(studentId.isBlank()) { toast("Escribe primero el ID en LEER.");return }
  val arr=history()
  arr.put(JSONObject().apply {
   put("student",studentId);put("exam",exam.title)
   put("correct",scan.correct(exam));put("total",exam.count)
   put("answers",scan.answers.joinToString(""));put("date",System.currentTimeMillis())
  })
  prefs.edit().putString("history",arr.toString()).apply()
  lastScan=null;scannedExam=null;render();toast("Calificación guardada")
 }
 private fun saveCsv(uri:Uri) {
  try {
   contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { out ->
    out.appendLine("ID;Prueba;Aciertos;Total;Respuestas;Fecha")
    val arr=history()
    for(i in 0 until arr.length()) {
     val j=arr.getJSONObject(i)
     val values=listOf("student","exam","correct","total","answers","date")
     out.appendLine(values.joinToString(";") { j.optString(it).replace(";"," ").replace("\n"," ") })
    }
   } ?: throw Exception("No se pudo crear el archivo")
   toast("CSV exportado")
  } catch(e:Exception){error("Error CSV",e)}
 }
 private fun error(title:String,e:Exception) {
  AlertDialog.Builder(this).setTitle(title).setMessage(e.message ?: "Error desconocido")
   .setPositiveButton("Aceptar",null).show()
 }
 private fun toast(s:String)=Toast.makeText(this,s,Toast.LENGTH_LONG).show()
}
