"use strict";
const API_URL="https://lsbwuwpvesdkwrwffjkc.supabase.co";
const PUB_KEY="sb_publishable_PYVfhbzVGaFlOqsl2sSQGA_FskOK_eB";
const ON_ANDROID=!!window.Native;
const state={session:null,workspace:null,exams:[],students:[],keys:[],scans:[],tab:"exams",examId:"",studentId:"",photo:"",compressed:"",answers:{},uncertain:[],scanNote:"",register:false,busy:false};
const root=document.getElementById("root"),queued={};
let seq=0,toastTimer=0;
function safe(x){return String(x==null?"":x).replace(/[&<>"']/g,c=>({"&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;","'":"&#39;"}[c]));}
function notice(msg){let el=document.getElementById("toast");el.textContent=msg;el.classList.remove("hidden");clearTimeout(toastTimer);toastTimer=setTimeout(()=>el.classList.add("hidden"),5500);}
function uuid(){return crypto.randomUUID?crypto.randomUUID():"xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx".replace(/[xy]/g,c=>{let r=Math.random()*16|0;return(c==="x"?r:(r&3|8)).toString(16)})}
function owner(){try{let v=state.session.access_token.split(".")[1].replace(/-/g,"+").replace(/_/g,"/");return JSON.parse(atob(v)).sub}catch(e){return"";}}
function nativeCall(op,data){return new Promise((resolve,reject)=>{const id=String(++seq);queued[id]={resolve,reject};window.Native.call(JSON.stringify(Object.assign({id,op},data||{})));});}
window.nativeReply=function(reply){const item=queued[reply.id];if(!item)return;delete queued[reply.id];reply.ok?item.resolve(reply.body):item.reject(Error(reply.error));};
async function request(method,path,data,binary,token){
 const auth=token===undefined?(state.session&&state.session.access_token||""):token;
 if(ON_ANDROID){
  const args={method,path,token:auth};
  if(data!==undefined&&data!==null)args.json=JSON.stringify(data);
  if(binary)args.binary=binary;
  const reply=await nativeCall("http",args);
  if(!reply)return null;
  try{return JSON.parse(reply)}catch(e){return reply;}
 }
 const headers={apikey:PUB_KEY,Accept:"application/json"};
 if(auth)headers.Authorization="Bearer "+auth;
 if(path.startsWith("/rest/v1/"))headers.Prefer=path.includes("on_conflict=")?"return=representation,resolution=merge-duplicates":"return=representation";
 let body;
 if(binary){body=Uint8Array.from(atob(binary),c=>c.charCodeAt(0));headers["Content-Type"]="image/webp";}
 else if(data!==undefined&&data!==null){headers["Content-Type"]="application/json";body=JSON.stringify(data);}
 const result=await fetch(API_URL+path,{method,headers,body});
 let text=await result.text();if(!result.ok)throw Error(result.status+" "+text.slice(0,220));
 try{return text?JSON.parse(text):null}catch(e){return text;}
}
async function api(method,path,data,binary){
 try{return await request(method,path,data,binary);}
 catch(e){
  if(state.session&&state.session.refresh_token&&/401|expired|JWT/i.test(e.message)){
   const next=await request("POST","/auth/v1/token?grant_type=refresh_token",{refresh_token:state.session.refresh_token},null,"");
   if(next&&next.access_token){state.session=next;localStorage.setItem("enciso_session",JSON.stringify(next));return request(method,path,data,binary);}
  }
  throw e;
 }
}
function saveCache(){localStorage.setItem("enciso_cache",JSON.stringify({workspace:state.workspace,exams:state.exams,students:state.students,keys:state.keys,scans:state.scans}));}
function restoreCache(){try{const cache=JSON.parse(localStorage.getItem("enciso_cache")||"{}");for(const x of ["workspace","exams","students","keys","scans"])if(cache[x])state[x]=cache[x];}catch(e){}}
function busy(b){state.busy=b;document.querySelectorAll("button.primary").forEach(el=>el.disabled=b);}
async function boot(){
 try{state.session=JSON.parse(localStorage.getItem("enciso_session")||"null");}catch(e){}
 restoreCache();render();
 if(state.session)try{await load();}catch(e){notice("Sin conexión: datos en caché. "+e.message);render();}
}
async function signIn(ev){
 ev.preventDefault();const email=document.getElementById("loginEmail").value.trim(),password=document.getElementById("loginPassword").value;
 busy(true);
 try{
  const path=state.register?"/auth/v1/signup":"/auth/v1/token?grant_type=password";
  const response=await request("POST",path,{email,password},null,"");
  if(response&&response.access_token){
   state.session=response;localStorage.setItem("enciso_session",JSON.stringify(response));
   state.workspace=null;state.exams=[];state.students=[];state.keys=[];state.scans=[];
   await load();
  }else notice("Cuenta creada. Confirma tu correo si Supabase lo solicita.");
 }catch(e){notice("Acceso: "+e.message)}finally{busy(false);render();}
}
function signOut(){state.session=null;state.workspace=null;state.exams=[];state.students=[];state.keys=[];state.scans=[];localStorage.removeItem("enciso_session");localStorage.removeItem("enciso_cache");render();}
async function load(){
 const myId=owner();if(!myId)throw Error("Sesión inválida");
 let arr=await api("GET","/rest/v1/omr_workspaces?select=*&order=created_at.asc&limit=1");
 if(!arr.length)arr=await api("POST","/rest/v1/omr_workspaces",{owner_id:myId,name:"Mi espacio OMR"});
 state.workspace=arr[0];await refresh();
}
async function refresh(){
 if(!state.workspace)return;
 let id=encodeURIComponent(state.workspace.id);
 const res=await Promise.all([
  api("GET","/rest/v1/omr_exams?workspace_id=eq."+id+"&select=*&order=created_at.desc"),
  api("GET","/rest/v1/omr_students?workspace_id=eq."+id+"&select=*&order=full_name.asc"),
  api("GET","/rest/v1/omr_answer_keys?workspace_id=eq."+id+"&select=*&order=item_number.asc"),
  api("GET","/rest/v1/omr_scans?workspace_id=eq."+id+"&select=*&order=scanned_at.desc")
 ]);
 [state.exams,state.students,state.keys,state.scans]=res;
 saveCache();render();
}
function count(exam){return Math.max(1,Math.min(100,Number(exam.settings&&exam.settings.count||40)));}
function choices(exam){return Math.max(2,Math.min(5,Number(exam.settings&&exam.settings.choices||4)));}
function examById(id){return state.exams.find(e=>e.id===id);}
function studentById(id){return state.students.find(s=>s.id===id);}
function examName(id){return examById(id)?examById(id).title:"Examen eliminado";}
function studentName(id){return studentById(id)?studentById(id).full_name:"Sin asignar";}
function choose(tab){state.tab=tab;render();}
function tile(num,label){return '<div class="metric"><strong>'+num+'</strong><span>'+safe(label)+'</span></div>';}
function frame(title,body){
 root.innerHTML='<header class="top"><div class="top-inner"><div><h1>EncisoOMR</h1><small>Lectura óptica de respuestas · Gratis</small></div><small>Supabase · ENCISOOMR</small></div></header>'+
 '<nav class="nav">'+[["exams","▤","Exámenes"],["scan","◎","Escanear"],["students","♙","Estudiantes"],["reports","▥","Resultados"],["settings","⚙","Cuenta"]].map(t=>'<button class="'+(state.tab===t[0]?"active":"")+'" onclick="choose(\''+t[0]+'\')"><span class="ico">'+t[1]+'</span>'+t[2]+'</button>').join("")+'</nav>'+
 '<main class="wrap"><div class="toolbar"><h2>'+safe(title)+'</h2><span class="pill">BETA 0.1.0</span></div>'+body+'</main>';
}
function render(){
 if(!state.session){
 root.innerHTML='<div class="wrap login"><div class="center"><h1>EncisoOMR</h1><p class="muted">Exámenes y lectura de respuestas</p></div><section class="box"><h3>'+(state.register?"Crear cuenta gratuita":"Iniciar sesión")+'</h3><form onsubmit="signIn(event)"><label>Correo electrónico</label><input id="loginEmail" type="email" autocomplete="email" required><label>Contraseña</label><input id="loginPassword" type="password" minlength="6" required><div style="height:16px"></div><button class="primary" style="width:100%">Continuar</button></form><div class="center" style="margin-top:13px"><button class="inline" onclick="state.register=!state.register;render()">'+(state.register?"Ya tengo cuenta":"Registrarme sin pagar")+'</button></div></section><p class="muted small center">Exámenes, imágenes y resultados privados en Supabase.</p></div>';return;
 }
 if(!state.workspace){frame("Conexión",'<section class="box"><p>Preparando tu espacio de trabajo…</p><button onclick="load()">Reintentar</button></section>');return;}
 const content={exams:examPanel,scan:scanPanel,students:studentPanel,reports:reportPanel,settings:settingsPanel}[state.tab]();
 frame(({exams:"Mis exámenes",scan:"Escanear hoja",students:"Estudiantes",reports:"Resultados",settings:"Mi cuenta"})[state.tab],content);
}
function examPanel(){
 let html='<div class="grid3">'+tile(state.exams.length,"Exámenes")+tile(state.students.length,"Estudiantes")+tile(state.scans.length,"Escaneos")+'</div>'+
 '<section class="box"><h3>Crear examen</h3><form onsubmit="createExam(event)"><label>Nombre</label><input id="examTitle" required maxlength="200" placeholder="Ej. Matemáticas · Grado 9"><div class="grid2"><div><label>Preguntas (1–100)</label><input type="number" id="examCount" min="1" max="100" value="40"></div><div><label>Opciones</label><select id="examOptions"><option>2</option><option>3</option><option selected>4</option><option>5</option></select></div></div><div style="height:13px"></div><button class="primary">Crear examen</button></form></section>'+
 '<section class="box"><h3>Exámenes guardados</h3>'+ (state.exams.length?state.exams.map(e=>'<div class="item"><div><b>'+safe(e.title)+'</b><p>'+count(e)+' preguntas · '+choices(e)+' opciones</p></div><button onclick="openExam(\''+e.id+'\')">Clave</button><button onclick="printSheet(\''+e.id+'\')">PDF</button></div>').join(""):'<p class="muted">Crea tu primer examen para comenzar.</p>')+'</section>';
 if(state.examId&&examById(state.examId))html+=keyPanel(examById(state.examId));
 return html;
}
async function createExam(ev){
 ev.preventDefault();
 const title=document.getElementById("examTitle").value.trim();
 const n=Number(document.getElementById("examCount").value),opts=Number(document.getElementById("examOptions").value);
 if(!title||n<1||n>100||opts<2||opts>5)return notice("Verifica los datos");
 try{busy(true);const created=await api("POST","/rest/v1/omr_exams",{owner_id:owner(),workspace_id:state.workspace.id,title,status:"ready",settings:{count:n,choices:opts}});state.examId=created[0].id;await refresh();notice("Examen creado");}
 catch(e){notice("Error: "+e.message)}finally{busy(false);}
}
function openExam(id){state.examId=state.examId===id?"":id;render();if(state.examId)setTimeout(()=>document.getElementById("keyPanel").scrollIntoView({behavior:"smooth"}),25);}
function keyPanel(e){
 const arr=state.keys.filter(k=>k.exam_id===e.id),keys=Object.fromEntries(arr.map(k=>[k.item_number,k]));
 let html='<section id="keyPanel" class="box"><div class="row space"><h3>Clave de '+safe(e.title)+'</h3><button onclick="printSheet(\''+e.id+'\')">Hoja A4</button></div><div class="notice">Configura las respuestas correctas. Puedes dejar preguntas sin clave o anularlas.</div>';
 for(let i=1;i<=count(e);i++){
  let key=keys[i]||{};
  html+='<div class="question"><span class="num">'+i+'.</span><div class="opts">'+Array.from({length:choices(e)},(_,j)=>{let letter="ABCDE"[j];return '<button class="bubble '+(key.answer===letter?"chosen":"")+'" data-item="'+i+'" data-option="'+letter+'" onclick="toggleKey(this)">'+letter+'</button>';}).join("")+'</div><label style="margin:0"><input type="checkbox" class="keyVoid" data-item="'+i+'" '+(key.is_voided?"checked":"")+' style="width:auto"> <small>Anular</small></label></div>';
 }
 return html+'<div style="height:13px"></div><button class="primary" onclick="saveKeys(\''+e.id+'\')">Guardar clave de respuestas</button></section>';
}
function toggleKey(button){let item=button.dataset.item,was=button.classList.contains("chosen");document.querySelectorAll('[data-item="'+item+'"][data-option]').forEach(x=>x.classList.remove("chosen"));if(!was)button.classList.add("chosen");}
async function saveKeys(id){
 const e=examById(id);if(!e)return;
 const values=[];for(let i=1;i<=count(e);i++){
  const chosen=document.querySelector('[data-item="'+i+'"][data-option].chosen');
  const voided=document.querySelector('.keyVoid[data-item="'+i+'"]');
  values.push({exam_id:id,workspace_id:state.workspace.id,owner_id:owner(),item_number:i,answer:chosen?chosen.dataset.option:null,is_voided:!!(voided&&voided.checked),points:1,penalty:0});
 }
 try{busy(true);await api("POST","/rest/v1/omr_answer_keys?on_conflict=exam_id,item_number",values);await refresh();notice("Clave guardada");}
 catch(e){notice(e.message)}finally{busy(false);}
}
function studentPanel(){
 return '<section class="box"><h3>Agregar estudiante</h3><form onsubmit="createStudent(event)"><label>Nombre completo</label><input id="studentName" required maxlength="200"><label>Código / identificación (opcional)</label><input id="studentCode" maxlength="80"><div style="height:14px"></div><button class="primary">Agregar estudiante</button></form></section>'+
 '<section class="box"><h3>Estudiantes · '+state.students.length+'</h3>'+(state.students.length?state.students.map(s=>'<div class="item"><div><b>'+safe(s.full_name)+'</b><p>'+safe(s.external_id||"Sin código")+'</p></div></div>').join(""):'<p class="muted">No has agregado estudiantes.</p>')+'</section>';
}
async function createStudent(ev){
 ev.preventDefault();
 const name=document.getElementById("studentName").value.trim(),external=document.getElementById("studentCode").value.trim()||null;
 try{busy(true);await api("POST","/rest/v1/omr_students",{workspace_id:state.workspace.id,owner_id:owner(),full_name:name,external_id:external});await refresh();notice("Estudiante guardado");}
 catch(e){notice(e.message)}finally{busy(false);}
}
function scanPanel(){
 let html='<section class="box"><h3>Tomar una fotografía</h3><label>Examen</label><select onchange="state.examId=this.value;state.photo=\'\';state.compressed=\'\';render()"><option value="">Selecciona…</option>'+
 state.exams.map(e=>'<option value="'+e.id+'" '+(state.examId===e.id?"selected":"")+'>'+safe(e.title)+'</option>').join("")+'</select><label>Estudiante</label><select onchange="state.studentId=this.value"><option value="">Sin asignar</option>'+
 state.students.map(s=>'<option value="'+s.id+'" '+(state.studentId===s.id?"selected":"")+'>'+safe(s.full_name)+'</option>').join("")+'</select><div style="height:15px"></div><div class="row"><button class="primary" '+(!state.examId?"disabled":"")+' onclick="selectPhoto(true)">📷 Cámara</button><button '+(!state.examId?"disabled":"")+' onclick="selectPhoto(false)">Importar foto</button></div><p class="muted small" style="margin-top:12px">Escaneo local experimental. La imagen se comprime en WebP antes de subir a Supabase.</p></section>';
 if(state.photo&&examById(state.examId)){
  const e=examById(state.examId);
  html+='<section class="box"><h3>Revisar respuestas</h3><img class="photo" src="'+state.photo+'" alt="Hoja escaneada"><p class="notice" style="margin-top:12px">'+safe(state.scanNote)+'</p><p class="muted small" style="margin-top:10px">'+state.uncertain.length+' respuestas requieren revisión. Puedes corregir todas manualmente.</p>';
  for(let i=1;i<=count(e);i++){
   const selected=state.answers[i]||"";
   html+='<div class="question"><span class="num">'+i+'.</span><div class="opts">'+Array.from({length:choices(e)},(_,j)=>{let a="ABCDE"[j];return '<button class="bubble '+(selected===a?"chosen":"")+' '+(state.uncertain.includes(i)?"review":"")+'" onclick="answer('+i+',\''+a+'\')">'+a+'</button>';}).join("")+
   '<button class="bubble '+(selected===""?"chosen":"")+'" onclick="answer('+i+',\'\')">—</button></div></div>';
  }
  html+='<div style="height:14px"></div><button class="primary" onclick="saveScan()">Guardar resultado e imagen WebP</button></section>';
 }
 return html;
}
function answer(i,v){state.answers[i]=v;state.uncertain=state.uncertain.filter(n=>n!==i);render();}
async function selectPhoto(fromCamera){
 if(!state.examId)return notice("Selecciona un examen.");
 try{
  if(ON_ANDROID){const data=await nativeCall(fromCamera?"camera":"gallery");await processPhoto(data);}
  else {
   const input=document.getElementById("photoPicker");input.value="";
   input.onchange=()=>{if(!input.files.length)return;const file=input.files[0],reader=new FileReader();reader.onload=()=>processPhoto(reader.result);reader.readAsDataURL(file);};
   input.click();
  }
 }catch(e){notice(e.message);}
}
async function processPhoto(src){
 state.photo=src;state.scanNote="Procesando fotografía…";state.answers={};state.uncertain=[];state.compressed="";render();
 try{
  const e=examById(state.examId),res=await EncisoRecognize(src,count(e),choices(e));
  state.answers=res.answers;state.uncertain=res.uncertain;state.compressed=res.webp;state.scanNote=res.message;render();
  notice("Foto procesada. Confirma la lectura.");
 }catch(err){state.scanNote="No pudo analizarse automáticamente. Corrige manualmente: "+err.message;render();}
}
function score(e,answers){
 const ref=Object.fromEntries(state.keys.filter(k=>k.exam_id===e.id).map(k=>[k.item_number,k]));
 let correct=0,total=0,points=0;
 for(let i=1;i<=count(e);i++){let k=ref[i];if(!k||k.is_voided||!k.answer)continue;total++;if(answers[i]===k.answer){correct++;points+=Number(k.points||1);}else if(answers[i])points-=Number(k.penalty||0);}
 return {correct,total,points:Math.round(points*1000)/1000};
}
async function saveScan(){
 const e=examById(state.examId);if(!e||!state.photo)return;
 if(!state.compressed){notice("La fotografía aún no tiene una conversión WebP válida.");return;}
 const binary=state.compressed.split(",")[1];
 if(!binary||binary.length*.75>3*1024*1024){notice("Imagen demasiado grande. Usa una foto de menor resolución.");return;}
 const id=uuid(),imagePath=owner()+"/"+e.id+"/"+id+".webp";
 const grade=score(e,state.answers);
 try{
  busy(true);
  await api("POST","/storage/v1/object/omr-scans/"+imagePath,null,binary);
  await api("POST","/rest/v1/omr_scans",{
   id,exam_id:e.id,workspace_id:state.workspace.id,owner_id:owner(),student_id:state.studentId||null,
   image_path:imagePath,status:"graded",detected_answers:state.answers,reviewed_answers:state.answers,
   scan_metadata:{source:"EncisoOMR 0.1.0",correct:grade.correct,graded_items:grade.total,unverified:state.uncertain.length},
   score:grade.points
  });
  state.photo="";state.compressed="";state.answers={};state.uncertain=[];
  await refresh();state.tab="reports";render();notice("Imagen WebP y respuestas guardadas.");
 }catch(err){notice("No se guardó: "+err.message);}finally{busy(false);}
}
function reportPanel(){
 let html='<div class="grid3">'+tile(state.scans.length,"Escaneos")+tile(state.scans.filter(s=>s.status==="graded").length,"Calificados")+tile(state.scans.filter(s=>s.image_path).length,"Con fotografía")+'</div>'+
 '<section class="box"><h3>Resultados</h3><div class="tablewrap"><table><thead><tr><th>Estudiante</th><th>Examen</th><th>Puntos</th><th>Fecha</th></tr></thead><tbody>';
 html+=state.scans.map(s=>'<tr><td>'+safe(studentName(s.student_id))+'</td><td>'+safe(examName(s.exam_id))+'</td><td><b>'+safe(s.score)+'</b></td><td>'+safe(new Date(s.scanned_at).toLocaleDateString("es-CO"))+'</td></tr>').join("");
 html+='</tbody></table></div>'+(state.scans.length?"":'<p class="muted">Aún no hay hojas calificadas.</p>')+'</section>';
 return html;
}
function settingsPanel(){
 return '<section class="box"><h3>Conexión</h3><p><b>Supabase EncisoOMR</b></p><p class="small muted">lsbwuwpvesdkwrwffjkc</p><p style="margin-top:11px"><b>Usuario:</b> '+safe(state.session.user&&state.session.user.email||"Cuenta registrada")+'</p><p class="muted small">Las claves y fotografías se guardan en servicios privados con controles por usuario.</p><div style="height:15px"></div><div class="row"><button onclick="updateNow()">Actualizar datos</button><button class="danger" onclick="signOut()">Salir</button></div></section><section class="box"><h3>Alcance de la primera beta</h3><div class="notice">El reconocimiento óptico es experimental y solo reconoce nuestras hojas. Comprueba manualmente las respuestas y evita usar estas calificaciones en evaluaciones oficiales hasta validar su precisión.</div></section>';
}
async function updateNow(){try{await refresh();notice("Datos actualizados");}catch(e){notice(e.message);}}
function printSheet(id){
 const e=examById(id);if(!e)return;
 const n=count(e),opts=choices(e);
 const css='@page{size:A4;margin:0}html,body{margin:0;width:210mm;height:297mm;color:#000;font:10pt Arial,sans-serif}.sheet{width:210mm;height:297mm;position:relative}.marker{position:absolute;width:7mm;height:7mm;background:#000}.header{position:absolute;top:17mm;left:22mm;width:166mm;text-align:center;font-size:15pt;font-weight:bold}.identity{position:absolute;top:32mm;left:22mm;font-size:9pt}.q{position:absolute;height:4.3mm;width:78mm}.num{position:absolute;left:0;top:0;font-size:8pt}.ball{position:absolute;top:.15mm;width:4mm;height:4mm;border:.35mm solid #000;border-radius:50%;font-size:7pt;line-height:3.6mm;text-align:center}.foot{position:absolute;top:278mm;left:22mm;font-size:8pt}';
 let h='<html><head><meta charset="utf-8"><style>'+css+'</style></head><body><div class="sheet">';
 for(const [x,y] of [[8.5,8.5],[194.5,8.5],[8.5,281.5],[194.5,281.5]])h+='<span class="marker" style="left:'+x+'mm;top:'+y+'mm"></span>';
 h+='<div class="header">EncisoOMR · '+safe(e.title)+'</div><div class="identity">Estudiante: ______________________________________ &nbsp; Código: __________________</div><div class="foot">Rellena completamente cada círculo. Hoja EncisoOMR 0.1.0</div>';
 for(let i=0;i<n;i++){
  const x=i<50?22:111,y=45.85+(i%50)*4.45;
  h+='<div class="q" style="left:'+x+'mm;top:'+y+'mm"><span class="num">'+(i+1)+'.</span>';
  for(let j=0;j<opts;j++)h+='<span class="ball" style="left:'+(28.5+j*8.5)+'mm">'+String.fromCharCode(65+j)+'</span>';
  h+='</div>';
 }
 h+='</div></body></html>';
 if(ON_ANDROID)nativeCall("print",{html:h}).catch(e=>notice(e.message));
 else {const windowPrint=window.open("","_blank");if(!windowPrint)return notice("Permite las ventanas emergentes para imprimir");windowPrint.document.write(h);windowPrint.document.close();windowPrint.focus();setTimeout(()=>windowPrint.print(),400);}
}
boot();
