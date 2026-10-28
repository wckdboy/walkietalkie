package cc.wckd.walkietalkie;

import android.content.Context;
import android.os.Bundle;
import java.io.File;
import java.util.ArrayList;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** A bounded queue keeps slow or failed recognition from blocking the microphone. */
final class SpeechPipeline implements AutoCloseable {
 interface Listener {void line(String text,boolean complete,int latency,long endMs);}
 private final RemoteSpeech remote;
 private final Listener listener;
 private final Consumer<String> warning;
 private final ThreadPoolExecutor decoder=new ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(40));
 private final AtomicBoolean failed=new AtomicBoolean();
 private volatile boolean closed;
 SpeechPipeline(Context c,File directory,Listener listener,Consumer<String> warning) throws Exception {
  this.listener=listener;this.warning=warning;remote=new RemoteSpeech(c,directory);
 }
 void start(long endMs) throws Exception {Bundle data=new Bundle();data.putLong("end",endMs);remote.call(SpeechService.START,data,10);}
 void accept(float[] audio,long endMs){
  if(closed||failed.get())return;
  try{decoder.execute(()->{
   if(failed.get()||closed)return;
   Bundle data=new Bundle();data.putFloatArray("audio",audio);data.putLong("end",endMs);
   try{publish(remote.call(SpeechService.AUDIO,data,10));}catch(Exception error){fail(error);}
  });}catch(RejectedExecutionException error){fail(new IllegalStateException("Speech decoder fell behind"));}
 }
 void finish(){
  if(closed||failed.get())return;
  try{decoder.submit(()->{try{if(!failed.get())publish(remote.call(SpeechService.FINISH,new Bundle(),10));}catch(Exception error){fail(error);}}).get(12,TimeUnit.SECONDS);}
  catch(Exception error){fail(error);}
 }
 @SuppressWarnings("deprecation") private void publish(Bundle result){
  if(closed||failed.get())return;
  ArrayList<Bundle> lines=result.getParcelableArrayList("lines");
  if(lines!=null)for(Bundle line:lines)listener.line(line.getString("text",""),line.getBoolean("complete"),line.getInt("latency"),result.getLong("end"));
 }
 private void fail(Exception error){
  if(closed||!failed.compareAndSet(false,true))return;
  for(Runnable task:decoder.getQueue())if(task instanceof Future<?>)((Future<?>)task).cancel(false);
  decoder.getQueue().clear();remote.close();
  warning.accept(error.getMessage()==null?error.getClass().getSimpleName():error.getMessage());
 }
 boolean available(){return !closed&&!failed.get();}
 @Override public void close(){if(closed)return;closed=true;decoder.shutdownNow();remote.close();}
}
