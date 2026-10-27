package com.encisomath.roqueomr

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import java.io.OutputStream
import kotlin.math.ceil
import kotlin.math.min

/**
 * A4 points. Four black fiducials at constant positions.
 * All positions use upper-left origin, like Android Canvas.
 */
object SheetPdf {
 const val WIDTH = 595
 const val HEIGHT = 842
 val MARKERS = arrayOf(Pair(35.0,35.0),Pair(560.0,35.0),
                       Pair(35.0,807.0),Pair(560.0,807.0))
 fun columns(n:Int):Int = ceil(n/25.0).toInt().coerceIn(1,4)
 fun rows(n:Int):Int = ceil(n.toDouble()/columns(n)).toInt()
 fun spacing(n:Int):Double = if(rows(n)<2)25.0 else min(25.0,535.0/(rows(n)-1))
 fun bubble(n:Int,q:Int,option:Int):Pair<Double,Double> {
  val rowCount=rows(n)
  val col=q/rowCount; val row=q%rowCount
  val left=44.0+col*506.0/columns(n)
  return Pair(left+47.0+21.0*option,212.0+row*spacing(n))
 }

 fun write(exam:Exam, out:OutputStream) {
  val pdf=PdfDocument()
  try {
   val page=pdf.startPage(PdfDocument.PageInfo.Builder(WIDTH,HEIGHT,1).create())
   draw(page.canvas,exam)
   pdf.finishPage(page)
   pdf.writeTo(out)
  } finally { pdf.close() }
 }

 private fun draw(canvas:Canvas, exam:Exam) {
  val paint=Paint(Paint.ANTI_ALIAS_FLAG)
  fun color(hex:String)=Color.parseColor(hex)
  fun fillRect(x:Double,y:Double,w:Double,h:Double,col:String) {
   paint.style=Paint.Style.FILL;paint.color=color(col)
   canvas.drawRect(x.toFloat(),y.toFloat(),(x+w).toFloat(),(y+h).toFloat(),paint)
  }
  fun label(x:Double,y:Double,s:String,size:Double=10.0,bold:Boolean=false,col:String="#17212F") {
   paint.style=Paint.Style.FILL;paint.color=color(col);paint.textSize=size.toFloat()
   paint.typeface=Typeface.create("sans-serif",if(bold) Typeface.BOLD else Typeface.NORMAL)
   canvas.drawText(s,x.toFloat(),y.toFloat(),paint)
  }
  fun line(x:Double,y:Double,x2:Double,y2:Double,col:String="#D7DCE2") {
   paint.style=Paint.Style.STROKE;paint.strokeWidth=0.8f;paint.color=color(col)
   canvas.drawLine(x.toFloat(),y.toFloat(),x2.toFloat(),y2.toFloat(),paint)
   paint.style=Paint.Style.FILL
  }
  for ((x,y) in MARKERS) fillRect(x-9,y-9,18.0,18.0,"#000000")
  fillRect(55.0,43.0,485.0,34.0,"#152332")
  label(66.0,66.0,"ROQUE OMR",18.0,true,"#FFFFFF")
  label(405.0,65.0,"HOJA DE RESPUESTAS",8.0,true,"#BFF9EA")
  label(55.0,102.0,"PRUEBA",8.0,true,"#455567")
  val title=exam.title.take(38)
  label(105.0,102.0,title,if(title.length>27)8.0 else 10.0,true)
  label(55.0,126.0,"ESTUDIANTE",8.0,true,"#455567")
  line(135.0,129.0,390.0,129.0)
  label(399.0,126.0,"ID",8.0,true,"#455567")
  line(417.0,129.0,538.0,129.0)
  label(55.0,155.0,"Rellena completamente un solo circulo por pregunta. Usa lapiz o tinta oscura.",9.0)
  label(55.0,169.0,"No dobles la hoja ni marques los cuatro cuadrados negros de referencia.",9.0)
  line(55.0,180.0,540.0,180.0,"#152332")
  val cols=columns(exam.count);val rowCount=rows(exam.count)
  val width=506.0/cols
  for(col in 0 until cols) {
   val left=44.0+col*width
   label(left+7,194.0,"N.",8.0,true,"#455567")
   for(option in 0 until exam.choices) {
    label(left+44+21*option,194.0,('A'+option).toString(),8.0,true,"#455567")
   }
   line(left+4,200.0,left+width-7,200.0)
  }
  for(q in 0 until exam.count) {
   val col=q/rowCount;val row=q%rowCount;val left=44.0+col*width
   val y=212.0+row*spacing(exam.count)
   label(left+7,y+3,"%02d".format(q+1),8.7,true,"#334155")
   for(option in 0 until exam.choices) {
    val (x,cy)=bubble(exam.count,q,option)
    paint.color=color("#465365");paint.style=Paint.Style.STROKE;paint.strokeWidth=1f
    canvas.drawCircle(x.toFloat(),cy.toFloat(),5f,paint)
    paint.style=Paint.Style.FILL
   }
  }
  line(55.0,778.0,540.0,778.0)
  label(55.0,795.0,"ROQUE OMR / FORMATO A4 / v1.0",7.2,true,"#64748B")
  label(55.0,820.0,"Conserva los margenes completos para una lectura correcta.",7.2,false,"#64748B")
 }
}
