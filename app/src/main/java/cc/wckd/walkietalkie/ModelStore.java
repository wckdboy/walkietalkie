package cc.wckd.walkietalkie;

import android.content.Context;
import ai.moonshine.voice.*;
import java.io.*;
import java.security.MessageDigest;
import java.util.concurrent.TimeUnit;
import okhttp3.*;

final class ModelStore {
 static final String MODEL = "qwen2.5-3b-instruct-q4_0.gguf";
 static final long SIZE = 1997879712L;
 static final String SHA = "670d82d0fbee6289b661eb323612c65bd2e97ec8c65cdbd9131aa44d30e9816a";
 static final String URL = "https://huggingface.co/Qwen/Qwen2.5-3B-Instruct-GGUF/resolve/main/"+MODEL;
 static final ModelSpec STT = ModelSpec.stt("en", JNI.MOONSHINE_MODEL_ARCH_TINY_STREAMING,false);
 static final ModelSpec TTS = ModelSpec.tts("en_us","kokoro_af_heart");
 final Context context;
 final File llm, speech, voice;
 private volatile Call call;
 interface Progress { void update(String label, int percent); }
 ModelStore(Context c) {
  context=c.getApplicationContext();
  llm=new File(c.getNoBackupFilesDir(),MODEL);
  speech=ModelCache.directoryFor(c,STT,null);
  voice=ModelCache.directoryFor(c,TTS,null);
 }
 boolean ready() {
  AssetDownloader d=new AssetDownloader();
  return llm.length()==SIZE && new File(llm+".verified").exists() && d.isModelPresent(speech,STT) && d.isModelPresent(voice,TTS);
 }
 void cancel() { Call c=call; if(c!=null)c.cancel(); }
 void install(Progress p) throws Exception {
  if (llm.length()!=SIZE || !new File(llm+".verified").exists()) downloadQwen(p);
  if(Thread.currentThread().isInterrupted())throw new InterruptedException();
  AssetDownloader downloader=new AssetDownloader();
  downloader.ensureModelPresent(speech,STT,(name,i,total,done,size)->p.update("Moonshine · "+name,size>0?(int)((100L*(i-1)+100L*done/size)/total):0));
  downloader.ensureModelPresent(voice,TTS,(name,i,total,done,size)->p.update("Kokoro · "+name,size>0?(int)((100L*(i-1)+100L*done/size)/total):0));
  p.update("All models are on your phone",100);
 }
 private void downloadQwen(Progress p) throws Exception {
  File part=new File(llm+".part");
  if(part.length()>SIZE && !part.delete())throw new IOException("Remove the incomplete model in Android app storage and retry.");
  long offset=part.length();
  if(llm.getParentFile().getUsableSpace()<SIZE-offset+500_000_000L)throw new IOException("Free at least 3 GB of storage, then retry setup.");
  OkHttpClient client=new OkHttpClient.Builder().connectTimeout(30,TimeUnit.SECONDS).readTimeout(90,TimeUnit.SECONDS).build();
  if(offset<SIZE) {
   Request.Builder request=new Request.Builder().url(URL);
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
      done+=n; if(done>SIZE)throw new IOException("Unexpected model size.");
      out.write(buffer,0,n);
      long now=System.nanoTime();
      if(now-last>200_000_000L){p.update("Qwen 2.5 · "+(done/1_000_000)+" / 1998 MB",(int)(done*100/SIZE));last=now;}
     }
    }
   } finally {call=null;}
  }
  if(part.length()!=SIZE)throw new IOException("Download incomplete. Tap setup to resume.");
  p.update("Checking Qwen integrity…",100);
  MessageDigest digest=MessageDigest.getInstance("SHA-256");
  try(InputStream in=new FileInputStream(part)){byte[] b=new byte[1024*1024];int n;while((n=in.read(b))!=-1){if(Thread.currentThread().isInterrupted())throw new InterruptedException();digest.update(b,0,n);}}
  StringBuilder hash=new StringBuilder(); for(byte b:digest.digest())hash.append(String.format("%02x",b&255));
  if(!SHA.contentEquals(hash)){part.delete();throw new IOException("Model integrity check failed. Tap setup to download again.");}
  if(llm.exists() && !llm.delete())throw new IOException("Could not replace model.");
  if(!part.renameTo(llm))throw new IOException("Could not save model.");
  try(FileOutputStream marker=new FileOutputStream(llm+".verified")){marker.write(SHA.getBytes(java.nio.charset.StandardCharsets.UTF_8));}
 }
}
