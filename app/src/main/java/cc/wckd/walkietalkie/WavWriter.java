package cc.wckd.walkietalkie;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;

/** Incremental mono PCM16 writer; repairs the header when a recording is resumed. */
final class WavWriter implements AutoCloseable {
 static final int SAMPLE_RATE = 16000;
 private static final java.util.Set<String> active=new java.util.HashSet<>();
 private final RandomAccessFile file;
 private final String path;
 private long bytes;
 WavWriter(File target) throws IOException {
  path=target.getAbsolutePath();
  synchronized(active){
   if(!active.add(path))throw new IOException("This recording is already open.");
   RandomAccessFile opened;
   try{opened=new RandomAccessFile(target,"rw");}catch(IOException error){active.remove(path);throw error;}
   file=opened;
  }
  try{
   bytes = Math.max(0, file.length() - 44);
   bytes -= bytes % 2;
   file.setLength(44 + bytes);
   header();
   file.seek(44 + bytes);
  }catch(IOException error){try{file.close();}finally{synchronized(active){active.remove(path);}}throw error;}
 }
 void write(short[] pcm, int count) throws IOException {
  if (bytes + count * 2L > 0xffffffffL - 36) throw new IOException("Recording reached the WAV size limit. Start a new recording.");
  byte[] data = new byte[count * 2];
  for(int i=0;i<count;i++) { data[2*i]=(byte)pcm[i]; data[2*i+1]=(byte)(pcm[i]>>8); }
  file.write(data);
  bytes += data.length;
 }
 long durationMs() { return bytes * 1000 / (SAMPLE_RATE * 2L); }
 static void repair(File target) throws IOException {
  synchronized(active){
  if(active.contains(target.getAbsolutePath()))return;
  if(!target.exists())return;
  boolean repair;
  try(RandomAccessFile in=new RandomAccessFile(target,"r")){
   if(in.length()<44)repair=true;
   else{in.seek(40);repair=Integer.toUnsignedLong(Integer.reverseBytes(in.readInt()))!=in.length()-44;}
  }
  if(repair)try(WavWriter recovered=new WavWriter(target)){/* close repairs and syncs the header */}
  }
 }
 void checkpoint() throws IOException { long position=file.getFilePointer();header();file.getFD().sync();file.seek(position); }
 private void le(int value) throws IOException {file.write(value);file.write(value>>8);file.write(value>>16);file.write(value>>24);}
 private void header() throws IOException {
  file.seek(0);file.writeBytes("RIFF");le((int)(36+bytes));file.writeBytes("WAVEfmt ");le(16);
  file.write(new byte[]{1,0,1,0});le(SAMPLE_RATE);le(SAMPLE_RATE*2);file.write(new byte[]{2,0,16,0});
  file.writeBytes("data");le((int)bytes);
 }
 @Override public void close() throws IOException {try{checkpoint();}finally{try{file.close();}finally{synchronized(active){active.remove(path);}}}}
}
