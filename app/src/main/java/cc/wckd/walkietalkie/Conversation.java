package cc.wckd.walkietalkie;
import java.util.ArrayList;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.*;

final class Conversation {
 static final String SYSTEM="You are WalkieTalkie, a friendly voice assistant running offline on the user's phone. Speak in natural, short conversational sentences, usually one to three. Use a brief conversational filler in each reply when appropriate, such as 'So, yeah,' 'Oh, okay,' or 'Hmm, let me think.' Vary the wording; do not repeat the same opener. Never claim you looked something up or performed an action. You have no internet or tools. Do not use markdown, lists, emojis, or stage directions. Be honest when uncertain.";
 final ArrayList<String[]> turns=new ArrayList<>();
 static String clean(String s){return s.replace("<|", "< | ").replace("|>"," | >");}
 String prompt(String user,boolean fillers) {
  String system=fillers?SYSTEM:SYSTEM.replace("Use a brief conversational filler in each reply when appropriate, such as 'So, yeah,' 'Oh, okay,' or 'Hmm, let me think.' Vary the wording; do not repeat the same opener.","Use direct, natural phrasing.");
  StringBuilder out=new StringBuilder("<|im_start|>system\n").append(system).append("<|im_end|>\n");
  int start=turns.size(), chars=0;
  while(start>0 && chars+turns.get(start-1)[0].length()+turns.get(start-1)[1].length()<2400){start--;chars+=turns.get(start)[0].length()+turns.get(start)[1].length();}
  for(int i=start;i<turns.size();i++){String[] t=turns.get(i);out.append("<|im_start|>user\n").append(clean(t[0])).append("<|im_end|>\n<|im_start|>assistant\n").append(clean(t[1])).append("<|im_end|>\n");}
  return out.append("<|im_start|>user\n").append(clean(user)).append("<|im_end|>\n<|im_start|>assistant\n").toString();
 }
 void add(String user,String reply){turns.add(new String[]{user,reply});while(turns.size()>6)turns.remove(0);}
 void clear(){turns.clear();}
 static final class Utf8 {
  private final CharsetDecoder decoder=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPLACE).onUnmappableCharacter(CodingErrorAction.REPLACE);
  private ByteBuffer buffer=ByteBuffer.allocate(8192);
  String push(byte[] bytes){buffer.put(bytes);buffer.flip();CharBuffer out=CharBuffer.allocate(8192);decoder.decode(buffer,out,false);buffer.compact();out.flip();return out.toString();}
 }
}
