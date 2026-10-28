package cc.wckd.walkietalkie;

import android.content.Context;
import android.media.*;
import android.os.SystemClock;
import ai.moonshine.voice.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

final class VoiceEngine {
 interface Listener {
  void state(String state,String detail);
  void progress(String label,int percent);
  void partial(String text);
  void user(String text);
  void reply(String text);
  void metrics(String text);
  void backend(String text);
  void error(String message);
 }
 final ModelStore store;
 private final Context context;
 private final Listener ui;
 private final ExecutorService worker=Executors.newSingleThreadExecutor();
 private final AtomicInteger epoch=new AtomicInteger();
 private final Conversation history=new Conversation();
 private final Object outputLock=new Object();
 private volatile AudioTrack output;
 private volatile boolean recording=false, closed=false, loaded=false;
 private Transcriber stt;
 private ai.moonshine.voice.TextToSpeech tts;
 private int loadedMode=-1;
 private String lastLine="", liveLine="";
 private int sttMs=0;
 private volatile long turnStart;
 volatile boolean fillers=true;
 VoiceEngine(Context c,Listener l){context=c.getApplicationContext();store=new ModelStore(context);ui=l;}
 boolean ready(){return loaded;}
 void prepare(boolean download,int mode) {
  int id=epoch.incrementAndGet();
  worker.execute(()->{
   try {
    if(download) {ui.state("setup","Downloading models. Keep WalkieTalkie open.");store.install(ui::progress);}
    if(id!=epoch.get() || closed)return;
    if(!store.ready()){ui.state("missing","Download the models once to use WalkieTalkie offline.");return;}
    ui.state("loading","Opening models. The first GPU load compiles kernels.");
    if(stt==null){stt=new Transcriber();stt.setUpdateInterval(0.2);stt.loadFromFiles(store.speech.getAbsolutePath(),JNI.MOONSHINE_MODEL_ARCH_TINY_STREAMING);stt.addListener(this::onTranscript);}
    if(tts==null){tts=new ai.moonshine.voice.TextToSpeech(context).language("en_us").voice("kokoro_af_heart");tts.load();}
    if(!loaded || loadedMode!=mode){NativeLlm.ensureLinked();String backend=NativeLlm.load(store.llm.getAbsolutePath(),4,mode,context.getApplicationInfo().nativeLibraryDir,context.getCacheDir().getAbsolutePath());loadedMode=mode;ui.backend(backend);ui.metrics("Moonshine + Kokoro on CPU");}
    loaded=true;
    if(id==epoch.get())ui.state("ready","Everything stays on this phone.");
   }catch(Throwable e){loaded=false;report(e);}
  });
 }
 private void onTranscript(TranscriptEvent event) {
  if(event instanceof TranscriptEvent.LineTextChanged){liveLine=((TranscriptEvent.LineTextChanged)event).line.text;ui.partial(liveLine);}
  if(event instanceof TranscriptEvent.LineCompleted){TranscriptLine line=((TranscriptEvent.LineCompleted)event).line;if(line.text!=null && !line.text.trim().isEmpty()){lastLine+=(lastLine.trim().isEmpty()?"":" ")+line.text;sttMs=line.lastTranscriptionLatencyMs;recording=false;turnStart=SystemClock.elapsedRealtime();}}
 }
 void listen() {
  int id=epoch.incrementAndGet();recording=true;
  worker.execute(()->{
   if(id!=epoch.get()||!loaded)return;
   AudioRecord mic=null;int stream=-1;
   lastLine="";liveLine="";sttMs=0;
   try {
    ui.state("listening","Speak naturally. Pause when you’re done.");
    if(context.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)!=android.content.pm.PackageManager.PERMISSION_GRANTED)throw new SecurityException("Microphone permission is required.");
    int min=AudioRecord.getMinBufferSize(16000,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT);
    mic=new AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION,16000,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT,Math.max(min,16000));
    if(mic.getState()!=AudioRecord.STATE_INITIALIZED)throw new IllegalStateException("Microphone could not start.");
    stream=stt.createStream();stt.startStream(stream);mic.startRecording();
    short[] pcm=new short[320];long start=SystemClock.elapsedRealtime();
    while(recording && id==epoch.get() && SystemClock.elapsedRealtime()-start<30000){
     int n=mic.read(pcm,0,pcm.length);if(n<0)throw new IllegalStateException("Microphone read failed: "+n);
     if(n>0){float[] audio=new float[n];for(int i=0;i<n;i++)audio[i]=pcm[i]/32768f;stt.addAudioToStream(stream,audio,16000);}
    }
    mic.stop();mic.release();mic=null;
    stt.stopStream(stream);stt.freeStream(stream);stream=-1;
    if(id!=epoch.get())return;
    String text=lastLine.trim();
    if(text.isEmpty()){ui.state("ready","I didn’t catch that. Try speaking again.");return;}
    if(text.length()>1200)text=text.substring(0,1200);
    ui.user(text);respond(text,id);
   }catch(Throwable e){if(id==epoch.get())report(e);}
   finally{recording=false;if(mic!=null){try{mic.stop();}catch(Exception ignored){}mic.release();}if(stream>=0){try{stt.freeStream(stream);}catch(Exception ignored){}}}
  });
 }
 void finishListening(){turnStart=SystemClock.elapsedRealtime();recording=false;}
 void send(String text){int id=epoch.incrementAndGet();sttMs=0;turnStart=SystemClock.elapsedRealtime();ui.user(text);worker.execute(()->{if(id==epoch.get()&&loaded)try{respond(text,id);}catch(Throwable e){report(e);}});}
 private void respond(String text,int id) throws Exception {
  if(id!=epoch.get())return;
  ui.state("thinking","Finding the words…");ui.reply("");
  AtomicBoolean generationDone=new AtomicBoolean(false);
  AtomicReference<Throwable> audioError=new AtomicReference<>();
  long start=SystemClock.elapsedRealtime();AtomicLong firstToken=new AtomicLong(-1),firstSound=new AtomicLong(-1),synthMs=new AtomicLong(-1);
  Thread player=new Thread(()->{
   AudioTrack track=null;long frames=0;
   try{
    while(id==epoch.get()){
     long t=SystemClock.elapsedRealtime();TtsChunk chunk=tts.nextChunk();
     if(chunk==null){if(generationDone.get()&&!tts.isStreaming())break;Thread.sleep(10);continue;}
     if(id!=epoch.get())break;
     if(chunk.samples==null||chunk.samples.length==0)continue;
     synthMs.compareAndSet(-1,SystemClock.elapsedRealtime()-t);
     if(track==null){
      int bytes=Math.max(AudioTrack.getMinBufferSize(chunk.sampleRateHz,AudioFormat.CHANNEL_OUT_MONO,AudioFormat.ENCODING_PCM_FLOAT),chunk.sampleRateHz/5*4);
      track=new AudioTrack.Builder().setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()).setAudioFormat(new AudioFormat.Builder().setSampleRate(chunk.sampleRateHz).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).setEncoding(AudioFormat.ENCODING_PCM_FLOAT).build()).setBufferSizeInBytes(bytes).setTransferMode(AudioTrack.MODE_STREAM).build();
      synchronized(outputLock){output=track;if(id==epoch.get())track.play();}
     }
     if(id!=epoch.get())break;
     ui.state("speaking","Tap interrupt to take your turn.");
     int off=0;
     while(off<chunk.samples.length && id==epoch.get()){
      int n=track.write(chunk.samples,off,Math.min(2048,chunk.samples.length-off),AudioTrack.WRITE_BLOCKING);
      if(n<0)throw new IllegalStateException("Audio output failed: "+n);
      if(n==0){Thread.sleep(5);continue;}
      firstSound.compareAndSet(-1,SystemClock.elapsedRealtime()-turnStart);off+=n;frames+=n;
     }
    }
    if(track!=null)while(id==epoch.get() && (track.getPlaybackHeadPosition()&0xffffffffL)<frames)Thread.sleep(10);
   }catch(Throwable e){audioError.set(e);}
   finally{ synchronized(outputLock){if(track!=null){try{track.pause();track.flush();}catch(Exception ignored){}track.release();}if(output==track)output=null;}}
  },"walkietalkie-audio");
  StringBuilder reply=new StringBuilder();Conversation.Utf8 decoder=new Conversation.Utf8();
  player.start();
  try{
   NativeLlm.generate(history.prompt(text,fillers),220,bytes->{
    if(id!=epoch.get()||audioError.get()!=null)return false;
    String piece=decoder.push(bytes);if(piece.isEmpty())return true;
    firstToken.compareAndSet(-1,SystemClock.elapsedRealtime()-start);
    reply.append(piece);ui.reply(reply.toString());tts.pushText(piece);
    return id==epoch.get();
   });
   
  }finally{generationDone.set(true);if(id!=epoch.get())tts.cancelStream();else tts.endInput();boolean interrupted=false;while(player.isAlive()){try{player.join();}catch(InterruptedException e){interrupted=true;}}tts.cancelStream();if(interrupted)Thread.currentThread().interrupt();}
  if(id!=epoch.get())return;
  if(audioError.get()!=null)throw new IllegalStateException("Kokoro playback failed",audioError.get());
  String answer=reply.toString().trim();if(!answer.isEmpty())history.add(text,answer);
  ui.metrics("STT decode "+(sttMs>0?sttMs+" ms":"—")+"  ·  first token "+firstToken.get()+" ms\nTTS chunk "+synthMs.get()+" ms  ·  first audio queued "+firstSound.get()+" ms");
  ui.state("ready","Your turn. No connection needed.");
 }
 void cancel(){epoch.incrementAndGet();recording=false;try{NativeLlm.cancel();}catch(UnsatisfiedLinkError ignored){}synchronized(outputLock){if(output!=null)try{output.pause();output.flush();}catch(Exception ignored){}}}
 void clear(){cancel();worker.execute(()->history.clear());}
 void close(){closed=true;cancel();store.cancel();worker.shutdownNow();new Thread(()->{try{worker.awaitTermination(120,TimeUnit.SECONDS);}catch(InterruptedException ignored){}if(worker.isTerminated()){if(stt!=null)stt.close();if(tts!=null)tts.close();try{NativeLlm.close();}catch(UnsatisfiedLinkError ignored){}}},"walkietalkie-release").start();}
 private void report(Throwable e){String detail=e.getMessage();Throwable cause=e.getCause();if(cause!=null&&cause.getMessage()!=null)detail+=" · "+cause.getMessage();ui.error(detail==null?e.getClass().getSimpleName():detail);}
}
