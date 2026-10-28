package cc.wckd.walkietalkie;

import android.Manifest;
import android.app.Instrumentation;
import android.content.Context;
import android.os.SystemClock;
import android.widget.TextView;
import androidx.lifecycle.Lifecycle;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import ai.moonshine.voice.AssetDownloader;
import java.io.*;
import java.nio.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class RecordingTest {
 private final Instrumentation instrumentation=InstrumentationRegistry.getInstrumentation();
 private final Context context=instrumentation.getTargetContext();
 @BeforeClass public static void downloadSpeechOnly() throws Exception {
  Context c=InstrumentationRegistry.getInstrumentation().getTargetContext();
  ModelStore models=new ModelStore(c);
  new AssetDownloader().ensureModelPresent(models.speech,ModelStore.STT,(name,i,total,done,size)->{});
  assertTrue(models.speechReady());
  assertFalse("This test must not depend on Qwen or GPU availability",models.ready());
 }
 @Test public void aRealSpeechTranscribesAgainAfterPause() throws Exception {
  byte[] bytes;
  try(InputStream in=instrumentation.getContext().getAssets().open("two_cities.wav")){bytes=in.readAllBytes();}
  ByteBuffer wav=ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
  assertEquals("RIFF",new String(bytes,0,4,java.nio.charset.StandardCharsets.US_ASCII));
  int offset=12,data=-1,length=0,sampleRate=0;
  while(offset+8<=bytes.length){
   String chunk=new String(bytes,offset,4,java.nio.charset.StandardCharsets.US_ASCII);
   int size=wav.getInt(offset+4);
   if(chunk.equals("fmt ")){assertEquals(1,wav.getShort(offset+8));assertEquals(1,wav.getShort(offset+10));sampleRate=wav.getInt(offset+12);assertTrue(sampleRate==16000||sampleRate==48000);assertEquals(16,wav.getShort(offset+22));}
   if(chunk.equals("data")){data=offset+8;length=size;break;}
   offset+=8+size+(size&1);
  }
  assertTrue(data>0);assertTrue(length>0);
  int ratio=sampleRate/16000;
  float[] audio=new float[Math.min(20*16000,length/2/ratio)];
  for(int i=0;i<audio.length;i++){float sum=0;for(int j=0;j<ratio;j++)sum+=wav.getShort(data+(i*ratio+j)*2)/32768f;audio[i]=sum/ratio;}
  StringBuilder transcript=new StringBuilder();
  try(SpeechSession speech=new SpeechSession(new ModelStore(context).speech,(text,complete,latency)->{if(complete)transcript.append(text).append(" ");})){
   for(int pass=0;pass<3;pass++){
    transcript.setLength(0);speech.start();
    for(int i=0;i<audio.length;i+=1600)speech.accept(Arrays.copyOfRange(audio,i,Math.min(audio.length,i+1600)));
    speech.finish();
    String result=transcript.toString().toLowerCase(Locale.ROOT);
    assertTrue("Pass "+pass+": "+result,result.contains("best of times"));
    assertTrue(result,result.contains("worst of times"));
   }
  }
 }
 @Test public void bDeniedPermissionReportsErrorWithoutClosing() throws Exception {
  CountDownLatch ready=new CountDownLatch(1),failed=new CountDownLatch(1);
  AtomicReference<String> message=new AtomicReference<>("");
  VoiceEngine engine=new VoiceEngine(context,new VoiceEngine.Listener(){
   public void state(String s,String d){if(s.equals("ready"))ready.countDown();}
   public void segment(String t,long a,long b){} public void elapsed(long t){}
   public void progress(String l,int p){} public void partial(String t){}
   public void user(String t){} public void reply(String t){}
   public void metrics(String t){} public void backend(String t){}
   public void error(String t){message.set(t);failed.countDown();}
  });
  File file=new File(context.getCacheDir(),"denied.wav");
  try {
   engine.prepare(false,1);assertTrue(ready.await(90,TimeUnit.SECONDS));
   engine.record(file);assertTrue(failed.await(15,TimeUnit.SECONDS));
   assertTrue(message.get(),message.get().contains("permission"));
   assertFalse(file.exists());
  }finally{engine.close();}
 }
 @Test public void cRecordButtonDefaultGpuResumeBackgroundAndRecreate() throws Exception {
  instrumentation.getUiAutomation().grantRuntimePermission(context.getPackageName(),Manifest.permission.RECORD_AUDIO);
  context.getSharedPreferences("MainActivity",0).edit().clear().putInt("backend",1).commit();
  SessionStore store=new SessionStore(context);long previous=0;
  try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
   awaitButton(scenario,"Start recording");
   for(int pass=0;pass<3;pass++){
    scenario.onActivity(a->a.findViewById(R.id.mic).performClick());
    awaitButton(scenario,"Pause recording");
    SystemClock.sleep(5000);
    scenario.onActivity(a->a.findViewById(R.id.mic).performClick());
    awaitButton(scenario,"Resume recording");
    String id=context.getSharedPreferences("MainActivity",0).getString("session","");
    SessionStore.Session saved=store.read(id);
    assertTrue("Recording must append actual microphone PCM",saved.durationMs>previous+1000);previous=saved.durationMs;
    try(RandomAccessFile audio=new RandomAccessFile(store.audio(saved),"r")){
     audio.seek(40);long data=Integer.toUnsignedLong(Integer.reverseBytes(audio.readInt()));
     assertEquals(audio.length()-44,data);
    }
   }
   scenario.onActivity(a->a.findViewById(R.id.mic).performClick());SystemClock.sleep(2000);
   scenario.moveToState(Lifecycle.State.CREATED);SystemClock.sleep(2000);
   scenario.moveToState(Lifecycle.State.RESUMED);awaitButton(scenario,"Resume recording");
   scenario.recreate();awaitButton(scenario,"Resume recording");
   scenario.onActivity(a->a.findViewById(R.id.mic).performClick());SystemClock.sleep(2000);
   scenario.onActivity(a->a.findViewById(R.id.mic).performClick());awaitButton(scenario,"Resume recording");
  }
 }
 private void awaitButton(ActivityScenario<MainActivity> scenario,String expected) {
  long deadline=SystemClock.elapsedRealtime()+90000;
  AtomicReference<String> value=new AtomicReference<>("");
  while(SystemClock.elapsedRealtime()<deadline){
   scenario.onActivity(a->{String status=((TextView)a.findViewById(R.id.status)).getText().toString();
    assertFalse(((TextView)a.findViewById(R.id.detail)).getText().toString(),status.equals("Let’s get this sorted"));
    value.set(a.findViewById(R.id.mic).isEnabled()?((TextView)a.findViewById(R.id.mic)).getText().toString():"Disabled");});
   if(value.get().equals(expected))return;SystemClock.sleep(200);
  }
  fail("Expected "+expected+", got "+value.get());
 }
}
