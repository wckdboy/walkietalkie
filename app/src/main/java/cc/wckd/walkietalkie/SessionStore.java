package cc.wckd.walkietalkie;

import android.content.Context;
import android.util.AtomicFile;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** All recordings, transcript segments and chat replies stay in no-backup app storage. */
final class SessionStore {
 static final class Entry {
  String role, text;
  long startMs, endMs;
  Entry(String role,String text,long startMs,long endMs){this.role=role;this.text=text;this.startMs=startMs;this.endMs=endMs;}
 }
 static final class Session {
  String id=UUID.randomUUID().toString(), title="", kind="recording";
  long created=System.currentTimeMillis(), updated=created, durationMs=0;
  boolean pinned=false;
  final List<Entry> entries=new ArrayList<>();
  Session snapshot(){Session s=new Session();s.id=id;s.title=title;s.kind=kind;s.created=created;s.updated=updated;s.durationMs=durationMs;s.pinned=pinned;for(Entry e:entries)s.entries.add(new Entry(e.role,e.text,e.startMs,e.endMs));return s;}
  String displayTitle(){return title.trim().isEmpty()?(kind.equals("recording")?"Untitled recording":"New conversation"):title;}
  String transcript(){StringBuilder b=new StringBuilder();for(Entry e:entries)if(e.role.equals("transcript"))b.append(e.text).append("
");return b.toString().trim();}
  String exportText(){
   StringBuilder b=new StringBuilder(displayTitle()).append("

");
   for(Entry e:entries)b.append(e.role.equals("transcript")?stamp(e.startMs):e.role.equals("assistant")?"WalkieTalkie":"You").append("
").append(e.text).append("

");
   return b.toString();
  }
 }
 private final File directory;
 SessionStore(Context context){directory=new File(context.getNoBackupFilesDir(),"sessions");if(!directory.isDirectory()&&!directory.mkdirs())throw new IllegalStateException("Cannot open recording storage.");}
 File audio(Session s){return new File(directory,s.id+".wav");}
 private File metadata(String id){if(!id.matches("[0-9a-fA-F-]{36}"))throw new IllegalArgumentException("Invalid recording ID");return new File(directory,id+".json");}
 synchronized void save(Session s) throws IOException {
  try {
   JSONObject object=new JSONObject().put("id",s.id).put("title",s.title).put("kind",s.kind).put("created",s.created).put("updated",s.updated).put("durationMs",s.durationMs).put("pinned",s.pinned);
   JSONArray entries=new JSONArray();
   for(Entry e:s.entries)entries.put(new JSONObject().put("role",e.role).put("text",e.text).put("start",e.startMs).put("end",e.endMs));
   object.put("entries",entries);
   AtomicFile atomic=new AtomicFile(metadata(s.id));FileOutputStream out=null;
   try{out=atomic.startWrite();out.write(object.toString().getBytes(StandardCharsets.UTF_8));atomic.finishWrite(out);}
   catch(IOException error){if(out!=null)atomic.failWrite(out);throw error;}
  } catch(JSONException error){throw new IOException("Could not save transcript",error);}
 }
 synchronized Session read(String id) throws IOException {
  try{
   JSONObject object=new JSONObject(new String(new AtomicFile(metadata(id)).readFully(),StandardCharsets.UTF_8));
   Session s=new Session();s.id=id;s.title=object.optString("title");s.kind=object.optString("kind","recording");s.created=object.getLong("created");s.updated=object.optLong("updated",s.created);s.durationMs=object.optLong("durationMs");s.pinned=object.optBoolean("pinned");
   JSONArray entries=object.getJSONArray("entries");
   for(int i=0;i<entries.length();i++){JSONObject e=entries.getJSONObject(i);s.entries.add(new Entry(e.getString("role"),e.getString("text"),e.optLong("start"),e.optLong("end")));}
   File audio=audio(s);if(audio.exists())s.durationMs=Math.max(s.durationMs,Math.max(0,audio.length()-44)*1000/32000);
   return s;
  }catch(JSONException error){throw new IOException("Could not read transcript",error);}
 }
 synchronized List<Session> list() throws IOException {
  File[] files=directory.listFiles((dir,name)->name.endsWith(".json"));
  if(files==null)throw new IOException("Cannot read recording library.");
  List<Session> result=new ArrayList<>();
  for(File file:files)result.add(read(file.getName().replace(".json","")));
  return result;
 }
 synchronized void delete(Session s) throws IOException {
  File wav=audio(s);if(wav.exists()&&!wav.delete())throw new IOException("Audio could not be deleted.");
  new AtomicFile(metadata(s.id)).delete();
  if(metadata(s.id).exists())throw new IOException("Transcript could not be deleted.");
 }
 static List<Session> sorted(List<Session> sessions,String query,String kind,int order){
  List<Session> result=new ArrayList<>();
  String needle=query.toLowerCase(Locale.ROOT).trim();
  for(Session s:sessions)if((kind.equals("all")||s.kind.equals(kind))&&(s.displayTitle().toLowerCase(Locale.ROOT).contains(needle)||s.entries.stream().anyMatch(e->e.text.toLowerCase(Locale.ROOT).contains(needle))))result.add(s);
  Comparator<Session> comparator=switch(order){case 1->Comparator.comparingLong(s->s.created);case 2->Comparator.comparing(Session::displayTitle,String.CASE_INSENSITIVE_ORDER);case 3->Comparator.comparingLong((Session s)->s.durationMs).reversed();default->Comparator.comparingLong((Session s)->s.updated).reversed();};
  result.sort(Comparator.comparing((Session s)->!s.pinned).thenComparing(comparator));return result;
 }
 static String stamp(long ms){long seconds=Math.max(0,ms/1000);return seconds>=3600?String.format(Locale.US,"%d:%02d:%02d",seconds/3600,(seconds/60)%60,seconds%60):String.format(Locale.US,"%02d:%02d",seconds/60,seconds%60);}
}
