package cc.wckd.walkietalkie;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;

/** Incremental mono PCM16 writer; repairs the header when a recording is resumed. */
final class WavWriter implements AutoCloseable {
 static final int SAMPLE_RATE = 16000;
 private final RandomAccessFile file;
 private long bytes;
 WavWriter(File target) throws IOException {
  file = new RandomAccessFile(target, "rw");
  try{
   bytes = Math.max(0, file.length() - 44);
   bytes -= bytes % 2;
   file.setLength(44 + bytes);
   header();
   file.seek(44 + bytes);
  }catch(IOException error){file.close();throw error;}
 }
 void write(short[] pcm, int count) throws IOException {
  if (bytes + count * 2L > 0xffffffffL - 36) throw new IOException("Recording reached the WAV size limit. Start a new recording.");
  byte[] data = new byte[count * 2];
  for(int i=0;i<count;i++) { data[2*i]=(byte)pcm[i]; data[2*i+1]=(byte)(pcm[i]>>8); }
  file.write(data);
  bytes += data.length;
 }
 long durationMs() { return bytes * 1000 / (SAMPLE_RATE * 2L); }
 void checkpoint() throws IOException { long position=file.getFilePointer();header();file.getFD().sync();file.seek(position); }
 private void le(int value) throws IOException {file.write(value);file.write(value>>8);file.write(value>>16);file.write(value>>24);}
 private void header() throws IOException {
  file.seek(0);file.writeBytes("RIFF");le((int)(36+bytes));file.writeBytes("WAVEfmt ");le(16);
  file.write(new byte[]{1,0,1,0});le(SAMPLE_RATE);le(SAMPLE_RATE*2);file.write(new byte[]{2,0,16,0});
  file.writeBytes("data");le((int)bytes);
 }
 @Override public void close() throws IOException {try{checkpoint();}finally{file.close();}}
}
