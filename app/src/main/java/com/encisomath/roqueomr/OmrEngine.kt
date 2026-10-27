package com.encisomath.roqueomr

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Deterministic OMR for SheetPdf pages.
 * Finds four large black square fiducials, estimates a projective transform,
 * and checks the inner 3-point radius of every answer bubble.
 * Does NOT pretend to be a trained neural network.
 */
object OmrEngine {
 private data class Marker(val x:Double,val y:Double,val area:Int,val width:Int,val height:Int)
 private data class Point(val x:Double,val y:Double)
 class ScanException(message:String):Exception(message)

 fun scan(input:Bitmap, exam:Exam):ScanResult {
  val maxDimension=1800.0
  val ratio=min(1.0,maxDimension/max(input.width,input.height))
  val bitmap=if(ratio<1.0) Bitmap.createScaledBitmap(
   input,(input.width*ratio).roundToInt().coerceAtLeast(1),
   (input.height*ratio).roundToInt().coerceAtLeast(1),true) else input
  val w=bitmap.width;val h=bitmap.height
  val pixels=IntArray(w*h)
  bitmap.getPixels(pixels,0,w,0,0,w,h)
  val marks=findMarkers(pixels,w,h)
  val m=homography(SheetPdf.MARKERS.map { Point(it.first,it.second) },
                   marks.map { Point(it.x,it.y) })
  val answers=mutableListOf<Char>()
  val uncertain=mutableSetOf<Int>()
  val allScores=mutableListOf<List<Double>>()
  for(q in 0 until exam.count) {
   val scores=(0 until exam.choices).map { o ->
    val (x,y)=SheetPdf.bubble(exam.count,q,o)
    measure(pixels,w,h,m,x,y)
   }
   allScores.add(scores)
   val ordered=scores.indices.sortedByDescending { scores[it] }
   val first=ordered[0]
   val highest=scores[first]
   val second=if(ordered.size>1)scores[ordered[1]] else 0.0
   val answer:Char=when {
    highest<0.16 -> '-'
    highest<0.35 || second>=0.28 || (highest-second)<0.17 -> '?'
    else -> 'A'+first
   }
   if(answer=='?')uncertain.add(q)
   answers.add(answer)
  }
  return ScanResult(answers,uncertain,allScores,marks.size)
 }

 private fun measure(pix:IntArray,w:Int,h:Int,m:DoubleArray,x:Double,y:Double):Double {
  var dark=0;var total=0
  for(a in -4..4) for(b in -4..4) {
   val dx=a*0.75;val dy=b*0.75
   if(dx*dx+dy*dy>10.9)continue
   val point=map(m,x+dx,y+dy)
   val ix=point.x.roundToInt();val iy=point.y.roundToInt()
   if(ix !in 0 until w || iy !in 0 until h)continue
   val c=pix[iy*w+ix]
   val gray=((c shr 16 and 255)*299+(c shr 8 and 255)*587+
     (c and 255)*114)/1000
   if(gray<150)dark++
   total++
  }
  return if(total>0)dark.toDouble()/total else 0.0
 }

 private fun findMarkers(pix:IntArray,w:Int,h:Int):List<Marker> {
  val n=pix.size;val state=ByteArray(n)
  val queue=IntArray(n)
  val candidates=ArrayList<Marker>()
  // Restrict extremely small components that could be ordinary text.
  val minArea=max(55,n/40000)
  for(i in 0 until n) {
   if(state[i].toInt()!=0)continue
   if(!dark(pix[i])){state[i]=1;continue}
   var front=0;var end=1;queue[0]=i;state[i]=1
   var xmin=i%w;var xmax=xmin;var ymin=i/w;var ymax=ymin
   while(front<end) {
    val curr=queue[front++];val x=curr%w;val y=curr/w
    if(x<xmin)xmin=x
    if(x>xmax)xmax=x
    if(y<ymin)ymin=y
    if(y>ymax)ymax=y
    if(x>0) {
     val j=curr-1
     if(state[j].toInt()==0){state[j]=1;if(dark(pix[j]))queue[end++]=j}
    }
    if(x+1<w) {
     val j=curr+1
     if(state[j].toInt()==0){state[j]=1;if(dark(pix[j]))queue[end++]=j}
    }
    if(y>0) {
     val j=curr-w
     if(state[j].toInt()==0){state[j]=1;if(dark(pix[j]))queue[end++]=j}
    }
    if(y+1<h) {
     val j=curr+w
     if(state[j].toInt()==0){state[j]=1;if(dark(pix[j]))queue[end++]=j}
    }
   }
   val bw=xmax-xmin+1;val bh=ymax-ymin+1
   val density=end.toDouble()/(bw*bh)
   val aspect=bw.toDouble()/bh
   if(end>=minArea && bw>=12 && bh>=12 && aspect in 0.68..1.47 &&
      density>=0.68 && bw<=w/5 && bh<=h/5) {
    candidates.add(Marker((xmin+xmax)/2.0,(ymin+ymax)/2.0,end,bw,bh))
   }
  }
  if(candidates.size<4)throw ScanException(
   "No se detectaron las cuatro marcas negras. Fotografía la hoja completa, sin recortar.")
  val largest=candidates.maxOf { it.area }
  val eligible=candidates.filter { it.area>=largest*0.23 && it.width>=12 && it.height>=12 }
  if(eligible.size<4)throw ScanException(
   "Faltan marcas de calibración o están demasiado borrosas.")
  // Extremal square centers correspond to TL, TR, BL, BR in portrait orientation.
  val tl=eligible.minByOrNull { it.x+it.y }!!
  val tr=eligible.maxByOrNull { it.x-it.y }!!
  val bl=eligible.maxByOrNull { it.y-it.x }!!
  val br=eligible.maxByOrNull { it.x+it.y }!!
  val result=listOf(tl,tr,bl,br)
  if(result.distinct().size!=4)throw ScanException(
   "No se identificaron las cuatro esquinas. Aleja el teléfono e intenta de nuevo.")
  val topWidth=distance(tl,tr);val bottomWidth=distance(bl,br)
  val height=distance(tl,bl);val height2=distance(tr,br)
  if(min(topWidth,bottomWidth)<w*0.18 || min(height,height2)<h*0.22)
   throw ScanException("La hoja está demasiado lejos o parcialmente fuera de la imagen.")
  if(topWidth>bottomWidth*3 || bottomWidth>topWidth*3 ||
     height>height2*3 || height2>height*3)
   throw ScanException("Las marcas no forman una hoja válida. Toma otra fotografía.")
  return result
 }

 private fun dark(c:Int):Boolean {
  val r=c shr 16 and 255;val g=c shr 8 and 255;val b=c and 255
  return (299*r+587*g+114*b)/1000 < 112
 }
 private fun distance(a:Marker,b:Marker):Double {
  val dx=a.x-b.x;val dy=a.y-b.y
  return kotlin.math.sqrt(dx*dx+dy*dy)
 }

 private fun map(m:DoubleArray,x:Double,y:Double):Point {
  val divisor=m[6]*x+m[7]*y+1.0
  return Point((m[0]*x+m[1]*y+m[2])/divisor,
               (m[3]*x+m[4]*y+m[5])/divisor)
 }

 private fun homography(from:List<Point>,to:List<Point>):DoubleArray {
  val a=Array(8) { DoubleArray(9) }
  for(i in 0..3) {
   val x=from[i].x;val y=from[i].y
   val u=to[i].x;val v=to[i].y
   a[2*i]=doubleArrayOf(x,y,1.0,0.0,0.0,0.0,-u*x,-u*y,u)
   a[2*i+1]=doubleArrayOf(0.0,0.0,0.0,x,y,1.0,-v*x,-v*y,v)
  }
  for(col in 0..7) {
   var pivot=col
   for(row in col+1..7)if(abs(a[row][col])>abs(a[pivot][col]))pivot=row
   if(abs(a[pivot][col])<1e-9)throw ScanException("No fue posible calibrar la perspectiva.")
   val tmp=a[col];a[col]=a[pivot];a[pivot]=tmp
   val scalar=a[col][col]
   for(k in col..8)a[col][k]/=scalar
   for(row in 0..7)if(row!=col) {
    val factor=a[row][col]
    for(k in col..8)a[row][k]-=factor*a[col][k]
   }
  }
  return DoubleArray(8){a[it][8]}
 }
}
