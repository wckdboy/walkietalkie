package cc.wckd.walkietalkie;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.media.*;
import android.os.*;
import android.text.InputFilter;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;

public class MainActivity extends Activity implements VoiceEngine.Listener {
 private static final int BG=0xff101312, CARD=0xff1b211e, GREEN=0xffcdf6ae, TEXT=0xfff0f3ed, MUTED=0xff9aa69e;
 private LinearLayout base,conversation,setup;
 private ScrollView scroll;
 private TextView status,detail,live,measurement,downloadLabel,backendLabel;
 private Button mic,install,send;
 private ProgressBar progress;
 private EditText input;
 private VoiceEngine engine;
 private TextView assistantBubble;
 private String state="starting";
 private int mode=1;
 private boolean visible=true, hasFocus=false;
 private AudioManager audioManager;
 private AudioFocusRequest focus;
 private final Handler main=new Handler(Looper.getMainLooper());
 private long lastUi=0;
 private String pendingReply="";
 private final Runnable renderReply=()->{if(assistantBubble!=null){assistantBubble.setText(pendingReply.isEmpty()?"…":pendingReply);scroll.post(()->scroll.fullScroll(View.FOCUS_DOWN));}};
 private final String[] modes={"CPU · compatibility","Adreno GPU · OpenCL","Hexagon NPU · experimental","GPU + NPU · experimental 50/50 split"};
 @Override public void onCreate(Bundle b){
  super.onCreate(b);
  mode=getPreferences(0).getBoolean("load_in_progress",false)?0:getPreferences(0).getInt("backend",1);
  audioManager=(AudioManager)getSystemService(AUDIO_SERVICE);
  focus=new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT).setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()).setOnAudioFocusChangeListener(change->{if(change<0){engine.cancel();state("ready","Audio paused by another app.");}}).build();
  buildUi();
  engine=new VoiceEngine(this,this);
  engine.fillers=getPreferences(0).getBoolean("fillers",true);
  engine.prepare(false,mode);
 }
 private int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
 private GradientDrawable bg(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
 private TextView text(String s,int size,int color){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);t.setFontFeatureSettings("kern");return t;}
 private LinearLayout col(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
 private void gap(LinearLayout l,int n){View v=new View(this);l.addView(v,new LinearLayout.LayoutParams(1,dp(n)));}
 private Button button(String s){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextSize(15);b.setTextColor(TEXT);b.setBackground(bg(CARD,18));b.setMinHeight(dp(48));return b;}
 private void buildUi(){
  base=col();base.setBackgroundColor(BG);base.setPadding(dp(22),dp(14),dp(22),dp(12));setContentView(base);
  base.setOnApplyWindowInsetsListener((v,insets)->{android.graphics.Insets i=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.ime());base.setPadding(dp(22)+i.left,dp(12)+i.top,dp(22)+i.right,dp(12)+i.bottom);return insets;});
  LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);
  TextView brand=text("walkietalkie",27,TEXT);brand.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));header.addView(brand,new LinearLayout.LayoutParams(0,dp(48),1));
  Button settings=button("Engine");settings.setContentDescription("Choose inference engine and voice style");settings.setOnClickListener(v->settings());header.addView(settings,new LinearLayout.LayoutParams(dp(90),dp(44)));base.addView(header);
  gap(base,12);status=text("●  GETTING READY",11,GREEN);status.setLetterSpacing(0.14f);base.addView(status);gap(base,5);
  backendLabel=text(modes[mode]+"  /  Qwen 2.5 · 3B",11,MUTED);base.addView(backendLabel);gap(base,10);
  scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setClipToPadding(false);scroll.setVerticalScrollBarEnabled(false);
  LinearLayout body=col();scroll.addView(body);base.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
  LinearLayout intro=col();intro.setPadding(0,dp(12),0,dp(8));TextView title=text("A little more\nhuman.",38,TEXT);title.setTypeface(Typeface.create("sans-serif-light",Typeface.NORMAL));intro.addView(title);gap(intro,10);intro.addView(text("A voice, a thought, a reply.\nAll on your phone.",15,MUTED));gap(intro,14);intro.addView(new Wave(this),new LinearLayout.LayoutParams(-1,dp(64)));body.addView(intro);
  setup=col();setup.setPadding(dp(18),dp(18),dp(18),dp(18));setup.setBackground(bg(CARD,22));
  setup.addView(text("Make yourself at home",19,TEXT));gap(setup,10);
  setup.addView(text("01   Listen       Moonshine Tiny Streaming\n02   Think        Qwen 2.5 · 3B · Q4_0\n03   Speak       Kokoro · Heart",13,GREEN));gap(setup,12);
  setup.addView(text("One-time download: about 2.5–3 GB. Have 4 GB free and use Wi-Fi. Then airplane mode is welcome. English speech in this demo.",13,MUTED));gap(setup,12);
  downloadLabel=text("Models stay in private app storage.",12,MUTED);setup.addView(downloadLabel);
  progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);progress.setProgressTintList(android.content.res.ColorStateList.valueOf(GREEN));progress.setVisibility(View.GONE);setup.addView(progress);gap(setup,12);
  install=button("Download & set up");install.setTextColor(BG);install.setBackground(bg(GREEN,16));install.setOnClickListener(v->{install.setEnabled(false);engine.prepare(true,mode);});setup.addView(install,new LinearLayout.LayoutParams(-1,dp(50)));body.addView(setup);gap(body,16);
  conversation=col();body.addView(conversation);
  measurement=text("Measured timings appear after a reply.",10,MUTED);body.addView(measurement);gap(body,8);
  live=text("",15,GREEN);live.setMaxLines(3);base.addView(live);gap(base,4);
  detail=text("Preparing your private voice space…",12,MUTED);base.addView(detail);gap(base,10);
  mic=button("One moment…");mic.setTextColor(BG);mic.setBackground(bg(GREEN,25));mic.setEnabled(false);mic.setOnClickListener(v->micAction());base.addView(mic,new LinearLayout.LayoutParams(-1,dp(56)));gap(base,10);
  LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);input=new EditText(this);input.setSingleLine(true);input.setTextColor(TEXT);input.setHintTextColor(MUTED);input.setHint("Or type a thought…");input.setTextSize(14);input.setPadding(dp(14),0,dp(12),0);input.setBackground(bg(CARD,18));input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(1200)});input.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEND);row.addView(input,new LinearLayout.LayoutParams(0,dp(48),1));send=button("Send");send.setOnClickListener(v->sendText());LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(dp(72),dp(48));sp.leftMargin=dp(8);row.addView(send,sp);input.setOnEditorActionListener((v,id,event)->{if(id==android.view.inputmethod.EditorInfo.IME_ACTION_SEND){sendText();return true;}return false;});base.addView(row);
 }
 private boolean focus(){if(hasFocus)return true;hasFocus=audioManager.requestAudioFocus(focus)==AudioManager.AUDIOFOCUS_REQUEST_GRANTED;if(!hasFocus)detail.setText("Audio is busy in another app. Try again.");return hasFocus;}
 private void releaseFocus(){if(hasFocus){audioManager.abandonAudioFocusRequest(focus);hasFocus=false;}}
 private void micAction(){
  if(state.equals("listening")){engine.finishListening();detail.setText("Finishing your thought…");return;}
  if(state.equals("thinking")||state.equals("speaking")){engine.cancel();state("ready","Interrupted. Tap to speak when you’re ready.");return;}
  if(!engine.ready())return;
  if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},8);return;}
  if(focus()){state("listening","Opening microphone…");engine.listen();}
 }
 private void sendText(){String value=input.getText().toString().trim();if(value.isEmpty()||!state.equals("ready")||!engine.ready()||!focus())return;input.setText("");((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(input.getWindowToken(),0);state("thinking","Finding the words…");engine.send(value);}
 @Override public void onRequestPermissionsResult(int request,String[] p,int[] result){super.onRequestPermissionsResult(request,p,result);if(request==8&&result.length>0&&result[0]==PackageManager.PERMISSION_GRANTED&&visible)micAction();else detail.setText("Microphone permission is needed to talk. You can still type.");}
 private void settings(){
  if(!state.equals("ready")&&!state.equals("missing")&&!state.equals("error")){Toast.makeText(this,"Finish the current turn first.",Toast.LENGTH_SHORT).show();return;}
  LinearLayout panel=col();panel.setPadding(dp(22),dp(12),dp(22),0);
  panel.addView(text("Qwen acceleration",16,TEXT));Spinner spinner=new Spinner(this);spinner.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,modes));spinner.setSelection(mode);panel.addView(spinner);
  panel.addView(text("GPU is the default. NPU and mixed mode are experimental and need the phone’s FastRPC access. Speech models use CPU. Mixed mode is not always faster.",13,MUTED));gap(panel,14);
  Switch filler=new Switch(this);filler.setText("Natural conversational fillers");filler.setTextColor(TEXT);filler.setChecked(engine.fillers);panel.addView(filler);gap(panel,12);
  panel.addView(text("No server. No account. Chat history lives in memory and clears when the app closes. Latency varies with heat and model load.",12,MUTED));
  new AlertDialog.Builder(this).setTitle("Make it yours").setView(panel).setNegativeButton("Close",null).setNeutralButton("New chat",(d,w)->{engine.clear();conversation.removeAllViews();assistantBubble=null;state(engine.ready()?"ready":"missing",engine.ready()?"A fresh conversation.":"Download the models once to use WalkieTalkie offline.");}).setPositiveButton("Apply",(d,w)->{int next=spinner.getSelectedItemPosition();engine.fillers=filler.isChecked();getPreferences(0).edit().putInt("backend",next).putBoolean("fillers",engine.fillers).apply();boolean changed=mode!=next;mode=next;backendLabel.setText(modes[mode]+"  /  Qwen 2.5 · 3B");if(changed||!engine.ready())engine.prepare(false,mode);}).show();
 }
 @Override public void state(String value,String message){main.post(()->{
  if(isDestroyed())return;if(value.equals("loading"))getPreferences(0).edit().putBoolean("load_in_progress",true).commit();if(value.equals("ready")||value.equals("error"))getPreferences(0).edit().putBoolean("load_in_progress",false).apply();state=value;detail.setText(message);status.setText("●  "+switch(value){case "ready"->"OFFLINE & READY";case "listening"->"LISTENING";case "thinking"->"THINKING";case "speaking"->"SPEAKING";case "setup"->"DOWNLOADING MODELS";case "error"->"NEEDS ATTENTION";default->"GETTING READY";});
  boolean active=value.equals("listening")||value.equals("thinking")||value.equals("speaking");
  boolean busy=value.equals("setup")||value.equals("loading");
  mic.setEnabled(active||value.equals("ready"));mic.setText(value.equals("listening")?"Finish turn":active?"Interrupt":"Tap to speak");send.setEnabled(value.equals("ready"));input.setEnabled(value.equals("ready"));
  if(busy)getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
  setup.setVisibility(value.equals("missing")||value.equals("setup")?View.VISIBLE:View.GONE);progress.setVisibility(value.equals("setup")?View.VISIBLE:View.GONE);install.setEnabled(value.equals("missing"));
  if(!active){live.setText("");releaseFocus();}
 });}
 @Override public void progress(String label,int percent){main.post(()->{downloadLabel.setText(label);progress.setProgress(percent);});}
 @Override public void partial(String text){main.post(()->{if(state.equals("listening"))live.setText(text);});}
 private TextView bubble(String name,String words,int color){LinearLayout box=col();box.setPadding(dp(16),dp(14),dp(16),dp(16));box.setBackground(bg(color,20));box.addView(text(name,10,GREEN));gap(box,7);TextView content=text(words,17,TEXT);content.setTextIsSelectable(true);content.setLineSpacing(dp(3),1);box.addView(content);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(12);conversation.addView(box,lp);while(conversation.getChildCount()>20)conversation.removeViewAt(0);return content;}
 @Override public void user(String text){main.post(()->{bubble("YOU",text,0xff252e27);assistantBubble=null;live.setText("");});}
 @Override public void reply(String text){main.post(()->{if(assistantBubble==null)assistantBubble=bubble("WALKIETALKIE",text,CARD);pendingReply=text;long now=SystemClock.uptimeMillis();main.removeCallbacks(renderReply);if(now-lastUi>50){renderReply.run();lastUi=now;}else main.postDelayed(renderReply,50);});}
 @Override public void backend(String text){main.post(()->backendLabel.setText(text+" / Qwen 2.5 · 3B"));}
 @Override public void metrics(String text){main.post(()->measurement.setText(text));}
 @Override public void error(String message){state("error",message);main.post(()->{setup.setVisibility(View.VISIBLE);downloadLabel.setText(message);install.setText("Retry setup / load");install.setEnabled(true);});}
 @Override protected void onStop(){visible=false;releaseFocus();if(state.equals("listening")||state.equals("thinking")||state.equals("speaking")){engine.cancel();state("ready","Paused. Tap to start a new turn.");}super.onStop();}
 @Override protected void onStart(){super.onStart();visible=true;}
 @Override protected void onDestroy(){main.removeCallbacksAndMessages(null);engine.close();super.onDestroy();}
 private class Wave extends View {
  private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
  Wave(Context c){super(c);setContentDescription("WalkieTalkie sound wave");}
  @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);float[] heights={8,15,24,38,52,36,20,40,58,43,25,13,27,42,29,17,9};float step=dp(12),left=(getWidth()-(heights.length-1)*step)/2;paint.setStrokeWidth(dp(5));paint.setStrokeCap(Paint.Cap.ROUND);for(int i=0;i<heights.length;i++){paint.setColor(i<10?GREEN:0xff425544);float h=dp(heights[i])/2f;canvas.drawLine(left+i*step,getHeight()/2f-h,left+i*step,getHeight()/2f+h,paint);}}
 }
}
