"use strict";
const API_URL="https://lsbwuwpvesdkwrwffjkc.supabase.co";
const PUB_KEY="sb_publishable_PYVfhbzVGaFlOqsl2sSQGA_FskOK_eB";
const ON_ANDROID=!!window.Native;
const state={session:null,workspace:null,exams:[],students:[],keys:[],scans:[],tab:"exams",examId:"",studentId:"",photo:"",compressed:"",answers:{},uncertain:[],scanNote:"",register:false,busy:false,cloudStatus:'local',cloudError:'',syncing:false,resyncRequested:false};
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
  restoreCache();
  if(!state.workspace)state.workspace={id:uuid(),name:"Mi espacio OMR",created_at:new Date().toISOString()};
  try{state.session=JSON.parse(localStorage.getItem("enciso_session")||"null");}catch(e){state.session=null;}
  saveCache();render();
  window.addEventListener("online",scheduleSync);
  setTimeout(function(){syncNow();},100);
}
async function refresh(){saveCache();render();}
let syncTimer=null;
function scheduleSync(){clearTimeout(syncTimer);syncTimer=setTimeout(function(){syncNow();},700);}
async function syncNow(){
  if(state.syncing){state.resyncRequested=true;return;}
  if(typeof navigator!=="undefined"&&navigator.onLine===false){state.cloudStatus="local";render();return;}
  state.syncing=true;state.cloudStatus="syncing";
  try{
    if(!state.session||!state.session.access_token){
      // No user interface for sign-in. Supabase authenticates this installation anonymously.
      const anon=await request("POST","/auth/v1/signup",{data:{product:"EncisoOMR"}},null,"");
      if(!anon||!anon.access_token||!anon.refresh_token)throw Error("anonymous_session_unavailable");
      state.session=anon;localStorage.setItem("enciso_session",JSON.stringify(anon));
    }
    await cloudSync();state.cloudStatus="synced";state.cloudError="";
  }catch(error){
    // Cloud configuration or HTTP errors cannot block the app.
    state.cloudStatus="local";state.cloudError=String(error&&error.message||error);
  }finally{
    state.syncing=false;saveCache();render();
    if(state.resyncRequested){state.resyncRequested=false;scheduleSync();}
  }
}
async function cloudSync(){
  const account=owner();if(!account)throw Error("Supabase identity unavailable");
  const remote=await api("GET","/rest/v1/omr_workspaces?select=*&order=created_at.asc&limit=20");
  if(remote.length&&!state.exams.length&&!state.students.length&&!state.keys.length&&!state.scans.length){
    state.workspace=remote[0];
    const filter="workspace_id=eq."+encodeURIComponent(state.workspace.id)+"&select=*";
    const data=await Promise.all([
      api("GET","/rest/v1/omr_exams?"+filter+"&order=created_at.desc"),
      api("GET","/rest/v1/omr_students?"+filter+"&order=full_name.asc"),
      api("GET","/rest/v1/omr_answer_keys?"+filter+"&order=item_number.asc"),
      api("GET","/rest/v1/omr_scans?"+filter+"&order=scanned_at.desc")
    ]);
    [state.exams,state.students,state.keys,state.scans]=data;
    saveCache();return;
  }
  if(!remote.some(w=>w.id===state.workspace.id))
    await api("POST","/rest/v1/omr_workspaces?on_conflict=id",
       {id:state.workspace.id,owner_id:account,name:state.workspace.name||"Mi espacio OMR"});
  const workId=state.workspace.id,base=x=>Object.assign({},x,{owner_id:account,workspace_id:workId});
  if(state.exams.length)await api("POST","/rest/v1/omr_exams?on_conflict=id",state.exams.map(base));
  if(state.students.length)await api("POST","/rest/v1/omr_students?on_conflict=id",state.students.map(base));
  if(state.keys.length)await api("POST","/rest/v1/omr_answer_keys?on_conflict=exam_id,item_number",state.keys.map(base));
  for(const scan of state.scans){
    if(!scan.image_path){
      const photo=await EncisoStore.loadImage(scan.id);
      if(photo&&photo.startsWith("data:image/webp;base64,")){
        const path=account+"/"+scan.exam_id+"/"+scan.id+".webp";
        try{await api("POST","/storage/v1/object/omr-scans/"+path,null,photo.split(",")[1]);}
        catch(e){if(!/409|already exists|Duplicate/i.test(String(e.message)))throw e;}
        scan.image_path=path;
      }
    }
    await api("POST","/rest/v1/omr_scans?on_conflict=id",base(scan));
  }
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
 root.innerHTML='<header class="top"><div class="top-inner"><div><h1>EncisoOMR</h1><small>Lectura óptica de respuestas · Gratis</small></div><small>Guardado local · EncisoOMR</small></div></header>'+
 '<nav class="nav">'+[["exams","▤","Exámenes"],["scan","◎","Escanear"],["students","♙","Estudiantes"],["reports","▥","Resultados"],["settings","⚙","Cuenta"]].map(t=>'<button class="'+(state.tab===t[0]?"active":"")+'" onclick="choose(\''+t[0]+'\')"><span class="ico">'+t[1]+'</span>'+t[2]+'</button>').join("")+'</nav>'+
 '<main class="wrap"><div class="toolbar"><h2>'+safe(title)+'</h2><span class="pill">BETA 0.1.1</span></div>'+body+'</main>';
}

function render(){
  if(!state.workspace)state.workspace={id:uuid(),name:"Mi espacio OMR"};
  const pages={exams:examPanel,scan:scanPanel,students:studentPanel,reports:reportPanel,settings:settingsPanel};
  const content=(pages[state.tab]||examPanel)();
  frame(({exams:"Mis exámenes",scan:"Escanear hoja",students:"Estudiantes",reports:"Resultados",settings:"Configuración"})[state.tab]||"Mis exámenes",content);
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
  const exam={id:uuid(),owner_id:state.workspace.id,workspace_id:state.workspace.id,
    title,status:"ready",settings:{count:n,choices:opts},created_at:new Date().toISOString()};
  state.exams.unshift(exam);state.examId=exam.id;
  saveCache();render();notice("Examen guardado");scheduleSync();
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
  const exam=examById(id);if(!exam)return;
  const rows=[];
  for(let i=1;i<=count(exam);i++){
    const selected=document.querySelector('[data-item="'+i+'"][data-option].chosen');
    const voided=document.querySelector('.keyVoid[data-item="'+i+'"]');
    rows.push({exam_id:id,workspace_id:state.workspace.id,owner_id:state.workspace.id,
      item_number:i,answer:selected?selected.dataset.option:null,
      is_voided:!!(voided&&voided.checked),points:1,penalty:0});
  }
  state.keys=state.keys.filter(k=>k.exam_id!==id).concat(rows);
  saveCache();render();notice("Clave guardada");scheduleSync();
}

function studentPanel(){
 return '<section class="box"><h3>Agregar estudiante</h3><form onsubmit="createStudent(event)"><label>Nombre completo</label><input id="studentName" required maxlength="200"><label>Código / identificación (opcional)</label><input id="studentCode" maxlength="80"><div style="height:14px"></div><button class="primary">Agregar estudiante</button></form></section>'+
 '<section class="box"><h3>Estudiantes · '+state.students.length+'</h3>'+(state.students.length?state.students.map(s=>'<div class="item"><div><b>'+safe(s.full_name)+'</b><p>'+safe(s.external_id||"Sin código")+'</p></div></div>').join(""):'<p class="muted">No has agregado estudiantes.</p>')+'</section>';
}

async function createStudent(ev){
  ev.preventDefault();
  const full_name=document.getElementById("studentName").value.trim();
  const external_id=document.getElementById("studentCode").value.trim()||null;
  if(!full_name)return notice("Ingresa el nombre");
  if(external_id&&state.students.some(s=>s.external_id&&s.external_id.toLowerCase()===external_id.toLowerCase()))
    return notice("Ese código de estudiante ya existe");
  state.students.push({id:uuid(),workspace_id:state.workspace.id,owner_id:state.workspace.id,
    full_name,external_id,attributes:{},created_at:new Date().toISOString()});
  state.students.sort((a,b)=>a.full_name.localeCompare(b.full_name));
  saveCache();render();notice("Estudiante guardado");scheduleSync();
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
  const exam=examById(state.examId);if(!exam||!state.photo)return;
  if(!state.compressed||!state.compressed.startsWith("data:image/webp;base64,"))
    return notice("La fotografía no se pudo convertir a WebP");
  const base64=state.compressed.split(",")[1];
  if(!base64||base64.length*.75>3*1024*1024)return notice("Foto demasiado grande");
  const id=uuid(),grade=score(exam,state.answers),now=new Date().toISOString();
  try{
    busy(true);
    await EncisoStore.saveImage(id,state.compressed);
    state.scans.unshift({
      id,exam_id:exam.id,student_id:state.studentId||null,
      workspace_id:state.workspace.id,owner_id:state.workspace.id,image_path:null,status:"graded",
      detected_answers:Object.assign({},state.answers),
      reviewed_answers:Object.assign({},state.answers),
      scan_metadata:{source:"EncisoOMR 0.1.1",correct:grade.correct,
        graded_items:grade.total,unverified:state.uncertain.length,storage:"device"},
      score:grade.points,scanned_at:now,updated_at:now
    });
    state.photo="";state.compressed="";state.answers={};state.uncertain=[];state.tab="reports";
    await refresh();notice("Imagen WebP y resultado guardados en el dispositivo");scheduleSync();
  }catch(error){notice("Error de guardado: "+error.message);}
  finally{busy(false);}
}

function reportPanel(){
 let html='<div class="grid3">'+tile(state.scans.length,"Escaneos")+tile(state.scans.filter(s=>s.status==="graded").length,"Calificados")+tile(state.scans.filter(s=>s.image_path).length,"Con fotografía")+'</div>'+
 '<section class="box"><h3>Resultados</h3><div class="tablewrap"><table><thead><tr><th>Estudiante</th><th>Examen</th><th>Puntos</th><th>Fecha</th></tr></thead><tbody>';
 html+=state.scans.map(s=>'<tr><td>'+safe(studentName(s.student_id))+'</td><td>'+safe(examName(s.exam_id))+'</td><td><b>'+safe(s.score)+'</b></td><td>'+safe(new Date(s.scanned_at).toLocaleDateString("es-CO"))+'</td></tr>').join("");
 html+='</tbody></table></div>'+(state.scans.length?"":'<p class="muted">Aún no hay hojas calificadas.</p>')+'</section>';
 return html;
}

function settingsPanel(){
  const status=state.cloudStatus==="synced"?"Datos sincronizados con Supabase":
    state.cloudStatus==="syncing"?"Sincronizando…":"Datos guardados en este dispositivo";
  return '<section class="box"><h3>Estado de tus datos</h3><p><b>'+safe(status)+'</b></p>'+
    '<p class="muted small">La aplicación funciona sin registro, usuario o contraseña. Los cambios se conservan primero en este teléfono.</p>'+
    '<div style="height:14px"></div><button class="primary" onclick="syncNow()">Sincronizar ahora</button></section>'+
    '<section class="box"><h3>Almacenamiento y privacidad</h3>'+
    '<p class="muted small">Proyecto EncisoOMR. Fotografías WebP locales y almacenamiento privado en Supabase cuando esté habilitada la sincronización.</p>'+
    '<div class="notice" style="margin-top:12px">Desinstalar la aplicación o borrar sus datos puede hacerte perder tus evaluaciones y el acceso a las copias privadas en la nube si utilizas una sesión anónima. Conserva respaldos.</div></section>'+
    '<section class="box"><h3>Escáner experimental</h3>'+
    '<p class="muted small">Comprueba siempre las respuestas detectadas antes de usar sus calificaciones.</p></section>';
}
async function updateNow(){await syncNow();}

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
