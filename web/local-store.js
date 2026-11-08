/* EncisoOMR: local private photo store. Photos are never written to localStorage. */
window.EncisoStore = (function(){
  let connection;
  const fallback = new Map();
  function open(){
    if(connection)return connection;
    if(!("indexedDB" in window))return Promise.resolve(null);
    connection=new Promise(function(resolve,reject){
      const request=indexedDB.open("EncisoOMRLocal",1);
      request.onupgradeneeded=function(){
        const db=request.result;
        if(!db.objectStoreNames.contains("images"))db.createObjectStore("images",{keyPath:"id"});
      };
      request.onsuccess=function(){resolve(request.result);};
      request.onerror=function(){reject(request.error||Error("Almacenamiento local no disponible"));};
    });
    return connection;
  }
  async function saveImage(id,dataUrl){
    const db=await open();
    if(!db){fallback.set(id,dataUrl);return;}
    return new Promise(function(resolve,reject){
      const t=db.transaction("images","readwrite");
      t.objectStore("images").put({id,dataUrl,savedAt:Date.now()});
      t.oncomplete=resolve;
      t.onerror=function(){reject(t.error||Error("No se pudo guardar la fotografía"));};
      t.onabort=function(){reject(t.error||Error("No se pudo guardar la fotografía"));};
    });
  }
  async function loadImage(id){
    const db=await open();
    if(!db)return fallback.get(id)||null;
    return new Promise(function(resolve,reject){
      const t=db.transaction("images","readonly");
      const req=t.objectStore("images").get(id);
      req.onsuccess=function(){resolve(req.result?req.result.dataUrl:null);};
      req.onerror=function(){reject(req.error||Error("No se pudo leer la fotografía"));};
    });
  }
  return {saveImage,loadImage,open};
})();
