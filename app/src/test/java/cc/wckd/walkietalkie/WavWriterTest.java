package cc.wckd.walkietalkie;
import org.junit.Test;
import static org.junit.Assert.*;
import java.io.*;
import java.nio.*;
import java.nio.file.*;

public class WavWriterTest {
 @Test public void reopeningAfterInterruptedCaptureRepairsPlaybackLength() throws Exception {
  File file=File.createTempFile("walkietalkie-recover",".wav");
  try{
   try(WavWriter writer=new WavWriter(file)){writer.write(new short[]{10,20},2);}
   try(RandomAccessFile interrupted=new RandomAccessFile(file,"rw")){interrupted.seek(interrupted.length());interrupted.write(new byte[]{30,0,40,0});}
   WavWriter.repair(file);
   ByteBuffer bytes=ByteBuffer.wrap(Files.readAllBytes(file.toPath())).order(ByteOrder.LITTLE_ENDIAN);
   assertEquals(8,bytes.getInt(40));assertEquals(40,bytes.getShort(50));assertEquals(52,file.length());
  }finally{file.delete();}
 }
 @Test public void resumePreservesSamplesAndRepairsHeader() throws Exception {
  File file=File.createTempFile("walkietalkie-audio",".wav");
  try{
   short[] first={Short.MIN_VALUE,-1,0,1,Short.MAX_VALUE};
   try(WavWriter writer=new WavWriter(file)){writer.write(first,first.length);}
   try(RandomAccessFile corrupt=new RandomAccessFile(file,"rw")){corrupt.seek(40);corrupt.writeInt(0);}
   try(WavWriter writer=new WavWriter(file)){writer.write(new short[]{1234},1);}
   ByteBuffer b=ByteBuffer.wrap(Files.readAllBytes(file.toPath())).order(ByteOrder.LITTLE_ENDIAN);
   assertEquals(48,b.getInt(4));assertEquals(12,b.getInt(40));assertEquals(16000,b.getInt(24));
   for(int i=0;i<5;i++)assertEquals(first[i],b.getShort(44+i*2));
   assertEquals(1234,b.getShort(54));
  }finally{file.delete();}
 }
 @Test public void longCaptureStreamsWithoutAccumulatingAudio() throws Exception {
  File file=File.createTempFile("walkietalkie-long",".wav");
  try{try(WavWriter writer=new WavWriter(file)){short[] frame=new short[16000];for(int i=0;i<90;i++)writer.write(frame,frame.length);writer.checkpoint();assertEquals(90000,writer.durationMs());}assertEquals(44+90*32000,file.length());}finally{file.delete();}
 }
}
