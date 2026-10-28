package cc.wckd.walkietalkie;

import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.SystemClock;
import android.view.View;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
import java.io.*;
import java.util.*;

@RunWith(AndroidJUnit4.class)
public class UiSmokeTest {
 private final Instrumentation instrumentation=InstrumentationRegistry.getInstrumentation();
 private final Context context=instrumentation.getTargetContext();
 private void shot(String name) throws Exception {
  instrumentation.waitForIdleSync();SystemClock.sleep(600);
  Bitmap image=instrumentation.getUiAutomation().takeScreenshot();assertNotNull(image);
  File directory=new File(context.getExternalFilesDir(null),"screenshots");assertTrue(directory.isDirectory()||directory.mkdirs());
  try(OutputStream out=new FileOutputStream(new File(directory,name+".png"))){assertTrue(image.compress(Bitmap.CompressFormat.PNG,100,out));}
  image.recycle();
 }
 private void settle(){instrumentation.waitForIdleSync();SystemClock.sleep(1200);}
 @Test public void emulatorLaunchLibraryModelsAndTranscript() throws Exception {
  context.getSharedPreferences("MainActivity",0).edit().clear().commit();
  try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
   settle();
   scenario.onActivity(a->{assertEquals(View.VISIBLE,a.findViewById(R.id.setup).getVisibility());assertFalse(a.findViewById(R.id.mic).isEnabled());});
   shot("01-first-setup");
   scenario.onActivity(a->a.findViewById(R.id.settings).performClick());shot("02-engine-sheet");
   instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK);settle();
   scenario.onActivity(a->{a.findViewById(R.id.settings).performClick();});
   settle();
   // The bottom sheet owns its own window; locate its model button through the UI hierarchy.
   android.view.accessibility.AccessibilityNodeInfo tree=instrumentation.getUiAutomation().getRootInActiveWindow();
   java.util.List<android.view.accessibility.AccessibilityNodeInfo> nodes=tree.findAccessibilityNodeInfosByText("Hugging Face models");
   assertFalse(nodes.isEmpty());nodes.get(0).performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK);
   shot("03-hugging-face-models");
   instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK);settle();
  }
  // Fixture data verifies saved-session restoration and the real rendering path, not speech inference.
  SessionStore store=new SessionStore(context);SessionStore.Session s=new SessionStore.Session();s.title="A clearer plan for next week";s.durationMs=84000;s.pinned=true;
  s.entries.add(new SessionStore.Entry("transcript","I keep coming back to the same idea: leave a little more space between meetings so there is time to think.",0,23000));
  s.entries.add(new SessionStore.Entry("transcript","Let’s try two focus blocks next week. Tuesday morning for the proposal, and Thursday afternoon to review it together.",23000,64000));
  s.entries.add(new SessionStore.Entry("user","Reflect on that with me.",84000,84000));
  s.entries.add(new SessionStore.Entry("assistant","So, yeah, protecting a little breathing room sounds like the real goal. What would make those two focus blocks easier to keep?",84000,84000));
  try(WavWriter wav=new WavWriter(store.audio(s))){short[] audio=new short[16000];for(int i=0;i<84;i++)wav.write(audio,audio.length);}
  store.save(s);
  SessionStore.Session chat=new SessionStore.Session();chat.kind="chat";chat.title="A small idea worth exploring";chat.entries.add(new SessionStore.Entry("user","What makes an idea worth a weekend?",0,0));store.save(chat);
  context.getSharedPreferences("MainActivity",0).edit().putString("session",s.id).commit();
  try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
   settle();scenario.onActivity(a->{a.state("ready","Recording saved. Resume, or invite WalkieTalkie in.");assertEquals("A clearer plan for next week",((TextView)a.findViewById(R.id.session_title)).getText().toString());});
   shot("04-transcription-feed");
   scenario.onActivity(a->((android.widget.ScrollView)a.findViewById(R.id.scroll)).fullScroll(View.FOCUS_UP));shot("05-recording-overview");
   scenario.onActivity(a->a.findViewById(R.id.library).performClick());settle();shot("06-recording-library");
   instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK);settle();
   scenario.onActivity(a->{a.state("recording","Listening quietly. Pause to invite WalkieTalkie in.");a.partial("And one more thing I want to remember…");a.elapsed(91000);});shot("07-recording-state-fixture");
   scenario.onActivity(a->{assertEquals("Pause recording",((TextView)a.findViewById(R.id.mic)).getText().toString());a.elapsed(84000);a.state("ready","Saved here.");});
  }
  settle();
  // Storage round-trip, transcript search, pin precedence and deletion use actual device files.
  SessionStore.Session restored=store.read(s.id);assertEquals(4,restored.entries.size());assertTrue(restored.pinned);assertEquals(84000,restored.durationMs);
  assertEquals(s.id,SessionStore.sorted(Arrays.asList(chat,restored),"focus blocks","all",0).get(0).id);
  assertEquals(s.id,SessionStore.sorted(Arrays.asList(chat,restored),"","all",2).get(0).id);
  assertEquals(1,SessionStore.sorted(Arrays.asList(chat,restored),"","chat",0).size());
  store.delete(chat);assertFalse(store.list().stream().anyMatch(item->item.id.equals(chat.id)));
  File damaged=new File(context.getNoBackupFilesDir(),"sessions/"+UUID.randomUUID()+".json");
  try{
   try(FileOutputStream out=new FileOutputStream(damaged)){out.write("{broken".getBytes(java.nio.charset.StandardCharsets.UTF_8));}
   assertTrue(store.list().stream().anyMatch(item->item.id.equals(s.id)));
   assertEquals(1,store.unreadableCount);assertTrue(damaged.exists());
  }finally{assertTrue(damaged.delete());}
 }
}
