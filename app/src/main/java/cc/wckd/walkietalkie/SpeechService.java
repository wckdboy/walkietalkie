package cc.wckd.walkietalkie;

import android.app.Service;
import android.content.Intent;
import android.os.*;
import java.io.File;
import java.util.ArrayList;

/** Native speech lives here; microphone capture and WAV storage stay in the UI process. */
public final class SpeechService extends Service {
 static final int LOAD=1, START=2, AUDIO=3, FINISH=4, TEST_CRASH=99;
 private HandlerThread thread;
 private Messenger incoming;
 private SpeechSession speech;
 private ArrayList<Bundle> lines;
 private long endMs;
 @Override public void onCreate(){
  super.onCreate();thread=new HandlerThread("speech-native");thread.start();
  incoming=new Messenger(new Handler(thread.getLooper(),this::receive));
 }
 private boolean receive(Message request){
  if(request.what==TEST_CRASH&&BuildConfig.DEBUG){
   try{android.system.Os.kill(android.os.Process.myPid(),android.system.OsConstants.SIGABRT);}catch(Exception ignored){}
   return true;
  }
  Bundle result=new Bundle();lines=new ArrayList<>();
  try{
   Bundle data=request.getData();
   switch(request.what){
    case LOAD -> {
     if(speech!=null)speech.close();
     speech=new SpeechSession(new File(data.getString("path")),(text,complete,latency)->{
      Bundle line=new Bundle();line.putString("text",text);line.putBoolean("complete",complete);line.putInt("latency",latency);lines.add(line);
     });
    }
    case START -> {endMs=data.getLong("end");speech.start();}
    case AUDIO -> {endMs=data.getLong("end");speech.accept(data.getFloatArray("audio"));}
    case FINISH -> speech.finish();
    default -> throw new IllegalArgumentException("Unknown speech operation");
   }
   result.putParcelableArrayList("lines",lines);result.putLong("end",endMs);
  }catch(Throwable error){result.putString("error",error.getClass().getSimpleName()+": "+error.getMessage());}
  Message response=Message.obtain(null,request.what);response.arg1=request.arg1;response.setData(result);
  try{if(request.replyTo!=null)request.replyTo.send(response);}catch(RemoteException ignored){}
  return true;
 }
 @Override public IBinder onBind(Intent intent){return incoming.getBinder();}
 @Override public void onDestroy(){
  // No other services live in this process. Reclaim a hung decoder without freeing live handles.
  super.onDestroy();android.os.Process.killProcess(android.os.Process.myPid());
 }
}
