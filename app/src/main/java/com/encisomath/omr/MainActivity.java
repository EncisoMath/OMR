package com.encisomath.omr;
import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.*;
import android.provider.MediaStore;
import android.print.PrintManager;
import android.util.Base64;
import android.webkit.*;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;

public class MainActivity extends Activity {
  final String base="https://lsbwuwpvesdkwrwffjkc.supabase.co";
  final String key="sb_publishable_PYVfhbzVGaFlOqsl2sSQGA_FskOK_eB";
  WebView web, printer;
  ExecutorService executor=Executors.newSingleThreadExecutor();
  Handler ui=new Handler(Looper.getMainLooper());
  String photoRequest=""; Uri photoUri;
  @Override public void onCreate(Bundle b) {
    super.onCreate(b);
    web=new WebView(this); web.setBackgroundColor(0xfff4f7fb);
    WebSettings s=web.getSettings();s.setJavaScriptEnabled(true);s.setDomStorageEnabled(true);
    s.setAllowFileAccess(true);s.setAllowFileAccessFromFileURLs(false);s.setAllowUniversalAccessFromFileURLs(false);
    web.setWebViewClient(new WebViewClient());web.setWebChromeClient(new WebChromeClient());
    web.addJavascriptInterface(new Bridge(),"Native");setContentView(web);web.loadUrl("file:///android_asset/index.html");
  }
  class Bridge{
    @JavascriptInterface public void call(String json){
      try {
        JSONObject j=new JSONObject(json);String op=j.optString("op"),id=j.optString("id");
        if(op.equals("http"))executor.execute(()->http(j,id));
        else if(op.equals("camera"))ui.post(()->{photoRequest=id;startActivityForResult(new Intent(MainActivity.this,ScannerActivity.class),102);});
        else if(op.equals("gallery"))ui.post(()->photo(id,false));
        else if(op.equals("print"))ui.post(()->print(id,j.optString("html")));
        else result(id,false,"","Acción desconocida");
      }catch(Exception e){result("",false,"",e.toString());}
    }
  }
  void result(String id,boolean ok,String body,String error){
    try{JSONObject j=new JSONObject();j.put("id",id);j.put("ok",ok);j.put("body",body);j.put("error",error);
      ui.post(()->web.evaluateJavascript("window.nativeReply("+j.toString()+")",null));
    }catch(Exception e){}
  }
  static byte[] read(InputStream stream,int limit)throws Exception {
    try(InputStream input=stream;ByteArrayOutputStream out=new ByteArrayOutputStream()){
      byte[] buf=new byte[16384];int n,total=0;
      while((n=input.read(buf))!=-1){total+=n;if(total>limit)throw new IOException("Imagen demasiado grande");out.write(buf,0,n);}
      return out.toByteArray();
    }
  }
  void http(JSONObject j,String id){
    HttpURLConnection conn=null;
    try{
      String path=j.optString("path"),method=j.optString("method","GET").toUpperCase(java.util.Locale.ROOT);
      if(!path.startsWith("/")||path.startsWith("//")||path.contains("..")||path.contains("\\")||
         !(path.startsWith("/auth/v1/")||path.startsWith("/rest/v1/")||path.startsWith("/storage/v1/")))
        throw new Exception("Ruta inválida");
      if(!(method.equals("GET")||method.equals("POST")||method.equals("PATCH")||method.equals("DELETE")))
        throw new Exception("Método inválido");
      conn=(HttpURLConnection)new URL(base+path).openConnection();
      conn.setRequestMethod(method);conn.setConnectTimeout(20000);conn.setReadTimeout(25000);
      conn.setRequestProperty("apikey",key);conn.setRequestProperty("Accept","application/json");
      String token=j.optString("token");
      if(!token.isEmpty())conn.setRequestProperty("Authorization","Bearer "+token);
      if(path.startsWith("/rest/v1/"))conn.setRequestProperty("Prefer",path.contains("on_conflict=")?"resolution=merge-duplicates,return=representation":"return=representation");
      if(j.has("binary")){
        byte[] bytes=Base64.decode(j.getString("binary"),Base64.DEFAULT);
        conn.setRequestProperty("Content-Type","image/webp");conn.setDoOutput(true);
        try(OutputStream os=conn.getOutputStream()){os.write(bytes);}
      }else if(j.has("json")){
        byte[] bytes=j.getString("json").getBytes(StandardCharsets.UTF_8);
        conn.setRequestProperty("Content-Type","application/json");conn.setDoOutput(true);
        try(OutputStream os=conn.getOutputStream()){os.write(bytes);}
      }
      int code=conn.getResponseCode();InputStream in=code<400?conn.getInputStream():conn.getErrorStream();
      String text=in==null?"":new String(read(in,5_000_000),StandardCharsets.UTF_8);
      result(id,code>=200&&code<300,text,"HTTP "+code+" "+text);
    }catch(Exception e){result(id,false,"",e.toString());}
    finally{if(conn!=null)conn.disconnect();}
  }
  void photo(String id,boolean camera){
    photoRequest=id;
    try{
      Intent pick=new Intent(Intent.ACTION_OPEN_DOCUMENT);
      pick.setType("image/*");pick.addCategory(Intent.CATEGORY_OPENABLE);
      startActivityForResult(pick,101);
    }catch(Exception e){result(id,false,"",e.toString());}
  }
  @Override protected void onActivityResult(int req,int status,Intent data){
    super.onActivityResult(req,status,data);
    if(req!=101&&req!=102)return;
    final String id=photoRequest;
    if(status!=RESULT_OK){result(id,false,"","Escaneo cancelado");return;}
    final boolean internal=req==102;
    final String filePath=internal&&data!=null?data.getStringExtra("scan_file"):null;
    final Uri uri=!internal&&data!=null?data.getData():null;
    if(internal&&filePath==null||!internal&&uri==null){
      result(id,false,"","No se recibió la fotografía");return;
    }
    executor.execute(()->{
      try{
        byte[] bytes;
        if(internal){
          File photo=new File(filePath);
          String allowed=getCacheDir().getCanonicalPath()+File.separator;
          if(!photo.getCanonicalPath().startsWith(allowed))throw new Exception("Ruta no válida");
          try(InputStream in=new FileInputStream(photo)){bytes=read(in,11_000_000);}
          photo.delete();
        }else{
          try(InputStream in=getContentResolver().openInputStream(uri)){bytes=read(in,15_000_000);}
        }
        String type=internal?"image/jpeg":getContentResolver().getType(uri);
        if(type==null||!type.startsWith("image/"))type="image/jpeg";
        result(id,true,"data:"+type+";base64,"+Base64.encodeToString(bytes,Base64.NO_WRAP),"");
      }catch(Exception e){result(id,false,"",e.toString());}
    });
  }
  void print(String id,String html){
    try{
      if(printer!=null)printer.destroy();
      printer=new WebView(this);
      printer.setWebViewClient(new WebViewClient(){
        @Override public void onPageFinished(WebView view,String url){
          PrintManager pm=(PrintManager)getSystemService(PRINT_SERVICE);
          pm.print("EncisoOMR hoja",view.createPrintDocumentAdapter("EncisoOMR"),
            new android.print.PrintAttributes.Builder().setMediaSize(android.print.PrintAttributes.MediaSize.ISO_A4).build());
          result(id,true,"{}","");
        }
      });
      printer.loadDataWithBaseURL(base+"/",html,"text/html","UTF-8",null);
    }catch(Exception e){result(id,false,"",e.toString());}
  }
  @Override public void onBackPressed(){if(web.canGoBack())web.goBack();else super.onBackPressed();}
  @Override protected void onDestroy(){executor.shutdownNow();if(printer!=null)printer.destroy();if(web!=null)web.destroy();super.onDestroy();}
}
