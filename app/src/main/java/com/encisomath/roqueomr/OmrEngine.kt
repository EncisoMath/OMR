package com.encisomath.roqueomr

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Deterministic OMR for SheetPdf pages.
 * Finds six distinctive square-ring fiducials, verifies their A4 geometry,
 * and checks the inner 3-point radius of every answer bubble.
 * Does NOT pretend to be a trained neural network.
 */
object OmrEngine {
 private data class Marker(val x:Double,val y:Double,val area:Int,val width:Int,val height:Int)
 private data class Point(val x:Double,val y:Double)
 class ScanException(message:String):Exception(message)
 data class Pixel(val x:Double,val y:Double)
 data class DetailedScan(
  val result:ScanResult,
  val corners:List<Pixel>,
  val bubbleCenters:List<List<Pixel>>,
  val imageWidth:Int,
  val imageHeight:Int
 )

 fun scan(input:Bitmap, exam:Exam):ScanResult = scanDetailed(input,exam).result

 fun scanDetailed(input:Bitmap, exam:Exam):DetailedScan {
  val maxDimension=1300.0
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
  val bubbleCenters=mutableListOf<List<Pixel>>()
  for(q in 0 until exam.count) {
   bubbleCenters.add((0 until exam.choices).map { option ->
    val (bx,by)=SheetPdf.bubble(exam.count,q,option)
    val point=map(m,bx,by)
    Pixel(point.x,point.y)
   })
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
  val inv=1.0/ratio
  return DetailedScan(
   ScanResult(answers,uncertain,allScores,6),
   marks.map { Pixel(it.x*inv,it.y*inv) },
   bubbleCenters.map { row -> row.map { Pixel(it.x*inv,it.y*inv) } },
   input.width,input.height
  )
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


 /**
  * Only the new A4 sheet is accepted. A real guide is a 24pt solid black
  * square RING with a 9pt white core; a filled answer circle has a dark core
  * and cannot pass. We require all SIX guides and a dark header at its known
  * page coordinates. Never "guess" a page from four arbitrary dark blobs.
  */
 private fun findMarkers(pix:IntArray,w:Int,h:Int):List<Marker> {
  val n=pix.size
  val visited=ByteArray(n)
  val queue=IntArray(n)
  val candidates=ArrayList<Marker>()
  val minArea=max(32,n/70000)
  for(i in 0 until n) {
   if(visited[i].toInt()!=0)continue
   if(!dark(pix[i])) { visited[i]=1;continue }
   var front=0;var tail=1;queue[0]=i;visited[i]=1
   var xmin=i%w;var xmax=xmin;var ymin=i/w;var ymax=ymin
   while(front<tail) {
    val p=queue[front++];val x=p%w;val y=p/w
    if(x<xmin)xmin=x
    if(x>xmax)xmax=x
    if(y<ymin)ymin=y
    if(y>ymax)ymax=y
    if(x>0) { val z=p-1
     if(visited[z].toInt()==0){visited[z]=1;if(dark(pix[z]))queue[tail++]=z}
    }
    if(x+1<w) { val z=p+1
     if(visited[z].toInt()==0){visited[z]=1;if(dark(pix[z]))queue[tail++]=z}
    }
    if(y>0) { val z=p-w
     if(visited[z].toInt()==0){visited[z]=1;if(dark(pix[z]))queue[tail++]=z}
    }
    if(y+1<h) { val z=p+w
     if(visited[z].toInt()==0){visited[z]=1;if(dark(pix[z]))queue[tail++]=z}
    }
   }
   if(tail<minArea)continue
   val bw=xmax-xmin+1;val bh=ymax-ymin+1
   if(bw<11||bh<11||bw>w/8||bh>h/8)continue
   val aspect=bw.toDouble()/bh
   if(aspect !in 0.53..1.89)continue
   val density=tail.toDouble()/(bw*bh)
   if(density !in 0.48..0.965)continue
   val cx=(xmin+xmax)/2.0;val cy=(ymin+ymax)/2.0
   // Center must be light even when a marked answer bubble is solid black.
   var centerSum=0;var centerCount=0
   for(dy in -1..1)for(dx in -1..1) {
    val x=(cx+dx).roundToInt();val y=(cy+dy).roundToInt()
    if(x in 0 until w && y in 0 until h){
     centerSum+=gray(pix[y*w+x]);centerCount++
    }
   }
   if(centerCount==0||centerSum/centerCount<175)continue
   val probes=arrayOf(
    Pair(-.30,0.0),Pair(.30,0.0),Pair(0.0,-.30),Pair(0.0,.30),
    Pair(-.23,-.23),Pair(.23,-.23),Pair(-.23,.23),Pair(.23,.23)
   )
   var blackRing=0
   for((dx,dy) in probes){
    val x=(cx+dx*bw).roundToInt();val y=(cy+dy*bh).roundToInt()
    if(x in 0 until w && y in 0 until h && gray(pix[y*w+x])<145)blackRing++
   }
   if(blackRing<6)continue
   candidates.add(Marker(cx,cy,tail,bw,bh))
  }
  if(candidates.size<6)throw ScanException(
   "No se reconocen las seis guías de la hoja nueva. Usa el PDF v1.2 y encuadra todo el papel.")

  // Sorting by position is only a *proposal*: the geometry, size, two
  // halfway guides, AND a printed dark header must all match before capture.
  val tl=candidates.minByOrNull { it.x+it.y }!!
  val tr=candidates.maxByOrNull { it.x-it.y }!!
  val bl=candidates.maxByOrNull { it.y-it.x }!!
  val br=candidates.maxByOrNull { it.x+it.y }!!
  val corners=listOf(tl,tr,bl,br)
  if(corners.distinct().size!=4)throw ScanException("Esquinas incompletas.")
  val tw=distance(tl,tr);val bw=distance(bl,br)
  val lh=distance(tl,bl);val rh=distance(tr,br)
  if(min(tw,bw)<w*.24||min(lh,rh)<h*.32)throw ScanException(
   "Acerca la cámara: la hoja debe ocupar una mayor parte de la pantalla.")
  val ratio=(lh+rh)/(tw+bw)
  if(ratio !in 0.90..2.45 || max(tw,bw)/min(tw,bw)>1.95 ||
     max(lh,rh)/min(lh,rh)>1.95)throw ScanException(
   "Las cuatro esquinas no corresponden a una hoja A4 completa.")
  fun cross(a:Marker,b:Marker,c:Marker):Double =
   (b.x-a.x)*(c.y-a.y)-(b.y-a.y)*(c.x-a.x)
  val ring=listOf(tl,tr,br,bl)
  if((0..3).any { cross(ring[it],ring[(it+1)%4],ring[(it+2)%4])<=0.0 })
   throw ScanException("Geometría de guías inválida.")
  val sizes=corners.map { min(it.width,it.height).toDouble() }
  if(sizes.maxOrNull()!!/sizes.minOrNull()!!>2.15)
   throw ScanException("Las guías no tienen un tamaño coherente.")
  for(marker in corners){
   val relative=marker.width/((tw+bw)/2)
   if(relative !in 0.020..0.115)throw ScanException(
    "Una guía tiene dimensiones incompatibles con la hoja.")
  }
  val transform=homography(
   SheetPdf.MARKERS.map { Point(it.first,it.second) },
   corners.map { Point(it.x,it.y) }
  )
  val middle=ArrayList<Marker>()
  val assigned=candidates.filter { it !in corners }
  for((x,y) in SheetPdf.MID_MARKERS) {
   val expected=map(transform,x,y)
   val threshold=max(9.0,sizes.average()*1.25)
   val closest=assigned.filter { it !in middle }.minByOrNull { marker ->
    kotlin.math.hypot(marker.x-expected.x,marker.y-expected.y)
   } ?: throw ScanException("Falta una guía lateral.")
   if(kotlin.math.hypot(closest.x-expected.x,closest.y-expected.y)>threshold)
    throw ScanException("Las guías laterales no corresponden a esta plantilla.")
   val relativeSize=min(closest.width,closest.height)/sizes.average()
   if(relativeSize !in 0.47..2.20)throw ScanException("Guía lateral inválida.")
   middle.add(closest)
  }
  fun paperGray(x:Double,y:Double):Int {
   val p=map(transform,x,y)
   val xx=p.x.roundToInt();val yy=p.y.roundToInt()
   if(xx !in 0 until w||yy !in 0 until h)return 255
   return gray(pix[yy*w+xx])
  }
  // This header is printed by SheetPdf, and isn't present among answer bubbles.
  if(listOf(170.0,300.0,440.0).count { paperGray(it,59.0)<160 }<2)
   throw ScanException("No coincide la cabecera de la hoja Roque OMR v1.2.")
  if(paperGray(280.0,142.0)<125)
   throw ScanException("La hoja parece mal alineada o está sombreada.")
  return corners
 }

 private fun gray(c:Int):Int {
  val r=c shr 16 and 255;val g=c shr 8 and 255;val b=c and 255
  return (299*r+587*g+114*b)/1000
 }
 private fun dark(c:Int):Boolean = gray(c)<112
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
