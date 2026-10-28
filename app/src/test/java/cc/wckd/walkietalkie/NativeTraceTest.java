package cc.wckd.walkietalkie;
import org.junit.Test;
import static org.junit.Assert.*;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
public class NativeTraceTest {
 static byte[] join(byte[]... parts){ByteArrayOutputStream out=new ByteArrayOutputStream();for(byte[] p:parts)out.write(p,0,p.length);return out.toByteArray();}
 static byte[] integer(long v){ByteArrayOutputStream out=new ByteArrayOutputStream();while((v&~127L)!=0){out.write((int)(v&127)|128);v>>>=7;}out.write((int)v);return out.toByteArray();}
 static byte[] number(int field,long v){return join(integer(field*8),integer(v));}
 static byte[] blob(int field,byte[] v){return join(integer(field*8+2),integer(v.length),v);}
 static byte[] text(int field,String v){return blob(field,v.getBytes(StandardCharsets.UTF_8));}
 @Test public void extractsOnlyFaultingThreadStack(){
  byte[] frame=join(number(1,42),text(4,"decode_audio"),text(6,"/private/path/libmoonshine.so"));
  byte[] thread=join(number(1,71),blob(4,frame),text(5,"PRIVATE MEMORY"));
  byte[] trace=join(number(6,71),blob(10,text(2,"SIGSEGV")),blob(16,join(number(1,71),blob(2,thread))),text(14,"PRIVATE ABORT DATA"));
  String result=NativeTrace.describe(trace);assertTrue(result.contains("SIGSEGV"));assertTrue(result.contains("libmoonshine.so +0x2a decode_audio"));assertFalse(result.contains("PRIVATE"));assertFalse(result.contains("/private/"));
 }
 @Test public void malformedInputIsSafe(){assertEquals("Native trace could not be decoded.",NativeTrace.describe(new byte[]{(byte)130}));assertEquals("Native trace could not be decoded.",NativeTrace.describe(new byte[]{82,127}));}
 @Test public void oversizedInputIsRejected(){assertTrue(NativeTrace.describe(new byte[NativeTrace.LIMIT+1]).contains("limit"));}
}
