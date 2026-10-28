package cc.wckd.walkietalkie;

import android.app.ActivityManager;
import android.app.ApplicationExitInfo;
import android.content.Context;
import android.os.Build;
import java.text.DateFormat;
import java.util.Date;
import java.util.List;

/** Local, user-copyable diagnostics. Never includes recordings or transcript text. */
final class Diagnostics {
 static void phase(Context c,String phase){c.getSharedPreferences("diagnostics",0).edit().putString("phase",phase).putLong("time",System.currentTimeMillis()).commit();}
 static byte[] readTrace(java.io.InputStream input) throws java.io.IOException {java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();byte[] buffer=new byte[8192];while(out.size()<=NativeTrace.LIMIT){int count=input.read(buffer,0,Math.min(buffer.length,NativeTrace.LIMIT+1-out.size()));if(count<0)break;out.write(buffer,0,count);}return out.toByteArray();}
 static String describe(Context c){
  StringBuilder result=new StringBuilder("WalkieTalkie ").append(BuildConfig.VERSION_NAME)
    .append("\n").append(Build.MANUFACTURER).append(" ").append(Build.MODEL)
    .append(" · Android ").append(Build.VERSION.RELEASE).append(" / API ").append(Build.VERSION.SDK_INT);
  result.append("\nLast operation: ").append(c.getSharedPreferences("diagnostics",0).getString("phase","None"));
  try {
   ActivityManager manager=(ActivityManager)c.getSystemService(Context.ACTIVITY_SERVICE);
   List<ApplicationExitInfo> exits=manager.getHistoricalProcessExitReasons(c.getPackageName(),0,8);
   if(!exits.isEmpty()){
    ApplicationExitInfo exit=exits.stream().filter(e->e.getReason()==ApplicationExitInfo.REASON_CRASH_NATIVE||e.getReason()==ApplicationExitInfo.REASON_CRASH).findFirst().orElse(exits.get(0));
    result.append("\nPrevious exit: ").append(DateFormat.getDateTimeInstance().format(new Date(exit.getTimestamp())))
     .append("\nProcess: ").append(exit.getProcessName())
     .append("\nReason: ").append(exit.getReason()==ApplicationExitInfo.REASON_CRASH_NATIVE?"Native crash":exit.getReason()==ApplicationExitInfo.REASON_CRASH?"Java crash":String.valueOf(exit.getReason()))
     .append("\nMemory sample: ").append(exit.getPss()>0?exit.getPss()+" KB PSS":"unavailable");
    if(exit.getReason()==ApplicationExitInfo.REASON_CRASH_NATIVE){
     try(java.io.InputStream trace=exit.getTraceInputStream()){result.append("\n").append(trace==null?"Native stack unavailable.":NativeTrace.describe(readTrace(trace)));}
     catch(java.io.IOException error){result.append("\nNative stack unavailable.");}
    }
   }
  }catch(RuntimeException error){result.append("\nPrevious exit details unavailable.");}
  return result.toString();
 }
}
