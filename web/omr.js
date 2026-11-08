/* EncisoOMR · Local optical mark recognition beta.
   Built independently for the application's own A4 sheet.
   All reads must be verified by a person before using grades. */
window.EncisoRecognize=async function(imageUri,questionCount,choicesCount){
  var img=new Image();
  img.src=imageUri;
  await new Promise(function(resolve,reject){img.onload=resolve;img.onerror=function(){reject(Error("Fotografía ilegible"));};});
  var factor=Math.min(1,1050/img.naturalWidth,1500/img.naturalHeight);
  var w=Math.max(1,Math.round(img.naturalWidth*factor));
  var h=Math.max(1,Math.round(img.naturalHeight*factor));
  var canvas=document.createElement("canvas");canvas.width=w;canvas.height=h;
  var context=canvas.getContext("2d",{willReadFrequently:true});
  context.drawImage(img,0,0,w,h);
  var rgba=context.getImageData(0,0,w,h).data;
  var gray=new Uint8Array(w*h),black=new Uint8Array(w*h),seen=new Uint8Array(w*h);
  for(var i=0;i<gray.length;i++){
    var offset=i*4;
    gray[i]=rgba[offset]*0.299+rgba[offset+1]*0.587+rgba[offset+2]*0.114;
    black[i]=gray[i]<95?1:0;
  }
  var queue=new Int32Array(w*h),candidates=[];
  for(var y=0;y<h;y++)for(var x=0;x<w;x++){
    var start=y*w+x;if(seen[start]||!black[start])continue;
    var front=0,back=1,area=0,minx=x,maxx=x,miny=y,maxy=y;
    queue[0]=start;seen[start]=1;
    while(front<back){
      var pos=queue[front++],px=pos%w,py=(pos/w)|0;
      area++;if(px<minx)minx=px;if(px>maxx)maxx=px;
      if(py<miny)miny=py;if(py>maxy)maxy=py;
      var around=[px?pos-1:-1,px<w-1?pos+1:-1,py?pos-w:-1,py<h-1?pos+w:-1];
      for(var j=0;j<4;j++){var z=around[j];if(z>=0&&black[z]&&!seen[z]){seen[z]=1;queue[back++]=z;}}
    }
    var cw=maxx-minx+1,ch=maxy-miny+1,fill=area/(cw*ch);
    if(area>50&&area<w*h*0.008&&cw>8&&ch>8&&cw/ch>0.65&&cw/ch<1.5&&fill>0.65)
      candidates.push({x:(minx+maxx)/2,y:(miny+maxy)/2,area:area});
  }
  var corners=[
    candidates.filter(function(p){return p.x<w*.3&&p.y<h*.25}),
    candidates.filter(function(p){return p.x>w*.7&&p.y<h*.25}),
    candidates.filter(function(p){return p.x<w*.3&&p.y>h*.75}),
    candidates.filter(function(p){return p.x>w*.7&&p.y>h*.75})
  ];
  var answers={},uncertain=[],count=Math.min(100,questionCount);
  var webp=canvas.toDataURL("image/webp",0.85);
  if(corners.some(function(list){return !list.length;})){
    for(i=1;i<=count;i++)uncertain.push(i);
    return {answers:answers,uncertain:uncertain,webp:webp,message:"No se localizaron las cuatro guías. La lectura automática no está disponible; corrige las respuestas manualmente."};
  }
  var marks=corners.map(function(list,k){
    var idealx=k%2?w*.92:w*.08,idealy=k<2?h*.08:h*.92;
    list.sort(function(a,b){return b.area/(1+Math.hypot(b.x-idealx,b.y-idealy))-a.area/(1+Math.hypot(a.x-idealx,a.y-idealy));});
    return list[0];
  });
  var pairs=[[12,12],[198,12],[12,285],[198,285]],target=marks.map(function(m){return[m.x,m.y]});
  var homography=solve(pairs,target);
  if(!homography){
    for(i=1;i<=count;i++)uncertain.push(i);
    return {answers:answers,uncertain:uncertain,webp:webp,message:"No se pudo normalizar la perspectiva. Corrige la lectura manualmente."};
  }
  function map(u,v){
    var d=homography[6]*u+homography[7]*v+1;
    return [(homography[0]*u+homography[1]*v+homography[2])/d,
            (homography[3]*u+homography[4]*v+homography[5])/d];
  }
  for(i=0;i<count;i++){
    var col=i<50?0:1,row=i%50,mmY=48+row*4.45,values=[];
    for(var option=0;option<choicesCount;option++){
      var mmX=(col===0?52.5:141.5)+option*8.5,ink=0,total=0;
      for(var dx=-1.4;dx<=1.4;dx+=.4)for(var dy=-1.4;dy<=1.4;dy+=.4){
        if(dx*dx+dy*dy>1.96)continue;
        var pt=map(mmX+dx,mmY+dy),ix=Math.round(pt[0]),iy=Math.round(pt[1]);
        if(ix>=0&&ix<w&&iy>=0&&iy<h){total++;ink+=black[iy*w+ix];}
      }
      values.push(total?ink/total:0);
    }
    var sorted=values.slice().sort(function(a,b){return b-a;});
    var best=sorted[0],second=sorted[1]||0;
    if(best>0.2&&best-second>0.085)answers[i+1]="ABCDE"[values.indexOf(best)];
    else uncertain.push(i+1);
  }
  return {answers:answers,uncertain:uncertain,webp:webp,message:"Lectura experimental: "+(count-uncertain.length)+" marcas reconocidas. Revisa las respuestas antes de guardar."};

  function solve(src,dst){
    var matrix=[];
    for(var k=0;k<4;k++){
      var u=src[k][0],v=src[k][1],tx=dst[k][0],ty=dst[k][1];
      matrix.push([u,v,1,0,0,0,-u*tx,-v*tx,tx]);
      matrix.push([0,0,0,u,v,1,-u*ty,-v*ty,ty]);
    }
    for(var k=0;k<8;k++){
      var best=k;
      for(var n=k+1;n<8;n++)if(Math.abs(matrix[n][k])>Math.abs(matrix[best][k]))best=n;
      if(Math.abs(matrix[best][k])<1e-8)return null;
      var swap=matrix[k];matrix[k]=matrix[best];matrix[best]=swap;
      var pivot=matrix[k][k];for(var q=k;q<=8;q++)matrix[k][q]/=pivot;
      for(n=0;n<8;n++)if(n!==k){
        var ratio=matrix[n][k];
        for(q=k;q<=8;q++)matrix[n][q]-=ratio*matrix[k][q];
      }
    }
    return matrix.map(function(row){return row[8]});
  }
};
