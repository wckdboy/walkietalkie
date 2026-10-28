package cc.wckd.walkietalkie;

import ai.moonshine.voice.*;
import java.io.File;
import java.util.HashSet;
import java.util.Set;

/** Worker-thread confined: one checked native stream, with per-recording completion tracking. */
final class SpeechSession implements AutoCloseable {
 interface Listener { void line(String text, boolean complete, int latencyMs); }
 private int handle=-1, stream=-1, pendingSamples;
 private final Set<Long> completed=new HashSet<>();
 private final Listener listener;
 SpeechSession(File directory, Listener listener) {
  this.listener=listener;
  JNI.ensureLibraryLoaded();
  System.loadLibrary("walkietalkie-speech");
  int loaded=JNI.moonshineLoadTranscriberFromFiles(directory.getAbsolutePath(),
    JNI.MOONSHINE_MODEL_ARCH_TINY_STREAMING,new TranscriberOption[0]);
  check(loaded,"Open speech model");handle=loaded;
 }
 void start() {
  if(handle<0||stream>=0)throw new IllegalStateException("Speech stream is not ready.");
  int created=JNI.moonshineCreateStream(handle,0);check(created,"Create speech stream");
  stream=created;completed.clear();pendingSamples=0;
  try{check(JNI.moonshineStartStream(handle,stream),"Start speech stream");}
  catch(RuntimeException e){releaseStream();throw e;}
 }
 void accept(float[] audio) {
  if(stream<0)throw new IllegalStateException("Speech stream is closed.");
  check(addAudio(handle,stream,audio),"Read speech audio");
  pendingSamples+=audio.length;
  if(pendingSamples>=4000){pendingSamples=0;publish();}
 }
 private void publish() {
  Transcript transcript=JNI.moonshineTranscribeStream(handle,stream,0);
  if(transcript==null||transcript.lines==null)throw new IllegalStateException("Speech decoder returned no result.");
  for(TranscriptLine line:transcript.lines) {
   if(line==null||line.text==null||line.text.trim().isEmpty())continue;
   if(line.isComplete) {
    if(completed.add(line.id))listener.line(line.text.trim(),true,line.lastTranscriptionLatencyMs);
   }else if(line.isUpdated||line.hasTextChanged||line.isNew)listener.line(line.text,false,line.lastTranscriptionLatencyMs);
  }
 }
 void finish() {
  if(stream<0)return;
  try{check(JNI.moonshineStopStream(handle,stream),"Finish speech stream");publish();}
  finally{releaseStream();}
 }
 private void releaseStream(){if(stream>=0){int old=stream;stream=-1;JNI.moonshineFreeStream(handle,old);}completed.clear();}
 @Override public void close(){releaseStream();if(handle>=0){int old=handle;handle=-1;JNI.moonshineFreeTranscriber(old);}}
 private static void check(int code,String operation){if(code<0)throw new IllegalStateException(operation+": "+JNI.moonshineErrorToString(code));}
 private static native int addAudio(int handle,int stream,float[] audio);
}
