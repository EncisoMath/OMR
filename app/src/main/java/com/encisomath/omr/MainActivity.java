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
        else if(op.equals("camera")||op.equals("gallery"))ui.post(()->photo(id,op.equals("camera")));
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
      Intent i;
      if(camera){
        ContentValues cv=new ContentValues();cv.put(MediaStore.Images.Media.DISPLAY_NAME,"EncisoOMR_"+System.currentTimeMillis()+".jpg");
        cv.put(MediaStore.Images.Media.MIME_TYPE,"image/jpeg");
        cv.put(MediaStore.Images.Media.RELATIVE_PATH,"Pictures/EncisoOMR");
        photoUri=getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,cv);
        if(photoUri==null)throw new Exception("No se pudo crear foto");
        i=new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        i.putExtra(MediaStore.EXTRA_OUTPUT,photoUri);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        startActivityForResult(i,100);
      }else{
        i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("image/*");
        i.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,101);
      }
    }catch(Exception e){result(id,false,"",e.toString());}
  }
  @Override protected void onActivityResult(int req,int status,Intent data){
    super.onActivityResult(req,status,data);
    if(req!=100&&req!=101)return;
    if(status!=RESULT_OK){result(photoRequest,false,"","Captura cancelada");return;}
    Uri uri=req==100?photoUri:(data==null?null:data.getData());
    if(uri==null){result(photoRequest,false,"","Imagen inválida");return;}
    String id=photoRequest;
    executor.execute(()->{
      try{
        byte[] bytes=read(getContentResolver().openInputStream(uri),22_000_000);
        String mime=getContentResolver().getType(uri);if(mime==null||!mime.startsWith("image/"))mime="image/jpeg";
        result(id,true,"data:"+mime+";base64,"+Base64.encodeToString(bytes,Base64.NO_WRAP),"");
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
