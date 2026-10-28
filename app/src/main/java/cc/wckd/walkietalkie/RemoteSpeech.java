package cc.wckd.walkietalkie;

import android.content.*;
import android.os.*;
import java.io.File;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

final class RemoteSpeech implements AutoCloseable {
 private final Context context;
 private final HandlerThread replies=new HandlerThread("speech-replies");
 private final ConcurrentHashMap<Integer,CompletableFuture<Bundle>> pending=new ConcurrentHashMap<>();
 private final AtomicInteger ids=new AtomicInteger();
 private final CountDownLatch connected=new CountDownLatch(1);
 private final Messenger receiver;
 private volatile Messenger remote;
 private volatile boolean closed,bound;
 private volatile String failure;
 private final ServiceConnection connection=new ServiceConnection(){
  public void onServiceConnected(ComponentName name,IBinder binder){
   if(closed)return;
   try{binder.linkToDeath(()->failed("Speech process stopped"),0);remote=new Messenger(binder);}
   catch(RemoteException error){failed("Speech process stopped");}
   connected.countDown();
  }
  public void onServiceDisconnected(ComponentName name){failed("Speech process stopped");}
  public void onBindingDied(ComponentName name){failed("Speech connection ended");}
  public void onNullBinding(ComponentName name){failed("Speech service unavailable");}
 };
 RemoteSpeech(Context c,File directory) throws Exception {
  context=c.getApplicationContext();replies.start();
  receiver=new Messenger(new Handler(replies.getLooper(),message->{
   CompletableFuture<Bundle> future=pending.remove(message.arg1);if(future!=null)future.complete(message.getData());return true;
  }));
  try{
   bound=context.bindService(new Intent(context,SpeechService.class),connection,Context.BIND_AUTO_CREATE);
   if(!bound||!connected.await(10,TimeUnit.SECONDS))throw new IllegalStateException("Speech service did not connect");
   Bundle data=new Bundle();data.putString("path",directory.getAbsolutePath());call(SpeechService.LOAD,data,90);
  }catch(Exception error){close();throw error;}
 }
 Bundle call(int operation,Bundle data,int seconds) throws Exception {
  if(closed||failure!=null||remote==null)throw new IllegalStateException(failure==null?"Speech service is closed":failure);
  int id=ids.incrementAndGet();CompletableFuture<Bundle> future=new CompletableFuture<>();pending.put(id,future);
  Message request=Message.obtain(null,operation);request.arg1=id;request.replyTo=receiver;request.setData(data);
  try{
   if(failure!=null)throw new IllegalStateException(failure);
   remote.send(request);Bundle result=future.get(seconds,TimeUnit.SECONDS);
   if(result.containsKey("error"))throw new IllegalStateException(result.getString("error"));
   return result;
  }catch(TimeoutException error){failed("Speech decoder timed out");throw error;}
  finally{pending.remove(id);}
 }
 private void failed(String message){
  failure=message;connected.countDown();
  for(CompletableFuture<Bundle> task:pending.values())task.completeExceptionally(new IllegalStateException(message));
 }
 @Override public void close(){
  if(closed)return;closed=true;failed("Speech service closed");
  if(bound){bound=false;try{context.unbindService(connection);}catch(IllegalArgumentException ignored){}}
  replies.quitSafely();
 }
}
