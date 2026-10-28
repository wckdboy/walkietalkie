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
 static String describe(Context c){
  StringBuilder result=new StringBuilder("WalkieTalkie ").append(BuildConfig.VERSION_NAME)
    .append("\n").append(Build.MANUFACTURER).append(" ").append(Build.MODEL)
    .append(" · Android ").append(Build.VERSION.RELEASE).append(" / API ").append(Build.VERSION.SDK_INT);
  result.append("\nLast operation: ").append(c.getSharedPreferences("diagnostics",0).getString("phase","None"));
  try {
   ActivityManager manager=(ActivityManager)c.getSystemService(Context.ACTIVITY_SERVICE);
   List<ApplicationExitInfo> exits=manager.getHistoricalProcessExitReasons(c.getPackageName(),0,1);
   if(!exits.isEmpty()){
    ApplicationExitInfo exit=exits.get(0);
    result.append("\nPrevious exit: ").append(DateFormat.getDateTimeInstance().format(new Date(exit.getTimestamp())))
     .append("\nReason ").append(exit.getReason()).append(" · ").append(exit.getDescription())
     .append("\nMemory at exit: ").append(exit.getPss()).append(" KB PSS");
   }
  }catch(RuntimeException error){result.append("\nPrevious exit details unavailable.");}
  return result.toString();
 }
}
