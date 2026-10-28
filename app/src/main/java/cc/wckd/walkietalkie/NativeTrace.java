package cc.wckd.walkietalkie;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Reads only signal and crashing-thread stack metadata from Android's tombstone protobuf. */
final class NativeTrace {
 static final int LIMIT=2*1024*1024;
 static String describe(byte[] bytes){
  if(bytes.length>LIMIT)return "Native trace exceeds diagnostic limit.";
  try{
   Reader root=new Reader(bytes);long tid=-1;String signal="";Map<Long,byte[]> threads=new HashMap<>();
   while(root.next()){
    if(root.field==6)tid=root.number();
    else if(root.field==10){Reader s=new Reader(root.bytes());while(s.next())if(s.field==2)signal=s.string();else s.skip();}
    else if(root.field==16){Reader entry=new Reader(root.bytes());long key=-1;byte[] value=null;while(entry.next()){if(entry.field==1)key=entry.number();else if(entry.field==2)value=entry.bytes();else entry.skip();}if(value!=null)threads.put(key,value);}
    else root.skip();
   }
   StringBuilder result=new StringBuilder("Native signal: ").append(signal.isEmpty()?"unavailable":signal);
   byte[] thread=threads.get(tid);if(thread==null)return result.append("\nNative stack unavailable.").toString();
   Reader t=new Reader(thread);int count=0;
   while(t.next()){
    if(t.field!=4||count>=16){t.skip();continue;}
    Reader frame=new Reader(t.bytes());String function="",file="";long pc=0;
    while(frame.next()){switch(frame.field){case 1->pc=frame.number();case 4->function=frame.string();case 6->file=frame.string();default->frame.skip();}}
    int slash=Math.max(file.lastIndexOf('/'),file.lastIndexOf('\\'));if(slash>=0)file=file.substring(slash+1);
    result.append("\n#").append(count++).append(" ").append(file).append(" +0x").append(Long.toHexString(pc));if(!function.isEmpty())result.append(" ").append(function);
   }
   return result.toString();
  }catch(IllegalArgumentException error){return "Native trace could not be decoded.";}
 }
 private static final class Reader {
  final byte[] data;int pos,field,wire;
  Reader(byte[] data){this.data=data;}
  boolean next(){if(pos==data.length)return false;long key=varint();field=(int)(key>>>3);wire=(int)(key&7);if(field<=0)throw new IllegalArgumentException();return true;}
  long varint(){long value=0;for(int i=0;i<10;i++){if(pos>=data.length)throw new IllegalArgumentException();int b=data[pos++]&255;if(i==9&&(b&254)!=0)throw new IllegalArgumentException();value|=(long)(b&127)<<(i*7);if((b&128)==0)return value;}throw new IllegalArgumentException();}
  long number(){if(wire!=0)throw new IllegalArgumentException();return varint();}
  int size(){long n=varint();if(n<0||n>data.length-pos)throw new IllegalArgumentException();return (int)n;}
  byte[] bytes(){if(wire!=2)throw new IllegalArgumentException();int n=size();byte[] value=Arrays.copyOfRange(data,pos,pos+n);pos+=n;return value;}
  String string(){byte[] value=bytes();return new String(value,0,Math.min(256,value.length),StandardCharsets.UTF_8).replaceAll("[\\r\\n\\t]"," ");}
  void skip(){switch(wire){case 0->varint();case 1->advance(8);case 2->advance(size());case 5->advance(4);default->throw new IllegalArgumentException();}}
  void advance(int n){if(n>data.length-pos)throw new IllegalArgumentException();pos+=n;}
 }
}
