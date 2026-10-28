package cc.wckd.walkietalkie;

import android.content.Context;
import ai.moonshine.voice.*;
import java.io.*;
import java.security.MessageDigest;
import java.util.concurrent.TimeUnit;
import okhttp3.*;

final class ModelStore {
 static final ModelSpec STT = ModelSpec.stt("en", JNI.MOONSHINE_MODEL_ARCH_TINY_STREAMING,false);
 static final ModelSpec TTS = ModelSpec.tts("en_us","kokoro_af_heart");
 final Context context;
 File llm;
 final File speech, voice;
 ModelCatalog.Model model;
 int selected;
 private volatile Call call;
 interface Progress { void update(String label, int percent); }
 ModelStore(Context c) {
  context=c.getApplicationContext();
  select(c.getSharedPreferences("models",0).getInt("selected",2));
  speech=ModelCache.directoryFor(c,STT,null);
  voice=ModelCache.directoryFor(c,TTS,null);
 }
 void select(int index) {
  selected=Math.max(0,Math.min(ModelCatalog.MODELS.length-1,index));
  model=ModelCatalog.MODELS[selected];llm=new File(context.getNoBackupFilesDir(),model.file());
  context.getSharedPreferences("models",0).edit().putInt("selected",selected).apply();
 }
 boolean downloaded(int index) {ModelCatalog.Model m=ModelCatalog.MODELS[index];File f=new File(context.getNoBackupFilesDir(),m.file());return f.length()==m.bytes()&&new File(f+".verified").exists();}
 boolean speechReady(){return new AssetDownloader().isModelPresent(speech,STT);}
 boolean ready() {
  AssetDownloader d=new AssetDownloader();
  return llm.length()==model.bytes() && new File(llm+".verified").exists() && d.isModelPresent(speech,STT) && d.isModelPresent(voice,TTS);
 }
 void cancel() { Call c=call; if(c!=null)c.cancel(); }
 void install(Progress p) throws Exception {
  if (llm.length()!=model.bytes() || !new File(llm+".verified").exists()) downloadQwen(p);
  if(Thread.currentThread().isInterrupted())throw new InterruptedException();
  AssetDownloader downloader=new AssetDownloader();
  downloader.ensureModelPresent(speech,STT,(name,i,total,done,size)->p.update("Moonshine · "+name,size>0?(int)((100L*(i-1)+100L*done/size)/total):0));
  downloader.ensureModelPresent(voice,TTS,(name,i,total,done,size)->p.update("Kokoro · "+name,size>0?(int)((100L*(i-1)+100L*done/size)/total):0));
  p.update("All models are on your phone",100);
 }
 private void downloadQwen(Progress p) throws Exception {
  File part=new File(llm+".part");
  if(part.length()>model.bytes() && !part.delete())throw new IOException("Remove the incomplete model in Android app storage and retry.");
  long offset=part.length();
  if(llm.getParentFile().getUsableSpace()<model.bytes()-offset+500_000_000L)throw new IOException("Not enough free storage. Free space and tap setup to resume.");
  OkHttpClient client=new OkHttpClient.Builder().connectTimeout(30,TimeUnit.SECONDS).readTimeout(90,TimeUnit.SECONDS).build();
  if(offset<model.bytes()) {
   Request.Builder request=new Request.Builder().url(model.url());
   if(offset>0)request.header("Range","bytes="+offset+"-");
   call=client.newCall(request.build());
   try(Response response=call.execute()) {
    if(!response.isSuccessful() || response.body()==null)throw new IOException("Model download returned HTTP "+response.code()+". Tap setup to retry.");
    if(response.code()==206) {
     String range=response.header("Content-Range","");
     if(!range.startsWith("bytes "+offset+"-"))throw new IOException("Server returned an invalid resume range.");
    } else offset=0;
    try(InputStream in=response.body().byteStream(); RandomAccessFile out=new RandomAccessFile(part,"rw")) {
     out.setLength(offset); out.seek(offset);
     byte[] buffer=new byte[65536]; int n; long done=offset; long last=0;
     while((n=in.read(buffer))!=-1) {
      if(Thread.currentThread().isInterrupted())throw new InterruptedException();
      done+=n; if(done>model.bytes())throw new IOException("Unexpected model size.");
      out.write(buffer,0,n);
      long now=System.nanoTime();
      if(now-last>200_000_000L){p.update("Qwen 2.5 · "+(done/1_000_000)+" / "+(model.bytes()/1_000_000)+" MB",(int)(done*100/model.bytes()));last=now;}
     }
    }
   } finally {call=null;}
  }
  if(part.length()!=model.bytes())throw new IOException("Download incomplete. Tap setup to resume.");
  p.update("Checking Qwen integrity…",100);
  MessageDigest digest=MessageDigest.getInstance("SHA-256");
  try(InputStream in=new FileInputStream(part)){byte[] b=new byte[1024*1024];int n;while((n=in.read(b))!=-1){if(Thread.currentThread().isInterrupted())throw new InterruptedException();digest.update(b,0,n);}}
  StringBuilder hash=new StringBuilder(); for(byte b:digest.digest())hash.append(String.format("%02x",b&255));
  if(!model.sha().contentEquals(hash)){part.delete();throw new IOException("Model integrity check failed. Tap setup to download again.");}
  if(llm.exists() && !llm.delete())throw new IOException("Could not replace model.");
  if(!part.renameTo(llm))throw new IOException("Could not save model.");
  try(FileOutputStream marker=new FileOutputStream(llm+".verified")){marker.write(model.sha().getBytes(java.nio.charset.StandardCharsets.UTF_8));}
 }
}
