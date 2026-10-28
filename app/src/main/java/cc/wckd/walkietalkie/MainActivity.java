package cc.wckd.walkietalkie;

import android.Manifest;
import android.animation.ValueAnimator;
import android.content.*;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.media.*;
import android.os.*;
import android.text.*;
import android.view.*;
import android.view.inputmethod.*;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import com.google.android.material.bottomsheet.*;
import com.google.android.material.button.*;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.*;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.loadingindicator.LoadingIndicator;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.radiobutton.MaterialRadioButton;
import com.google.android.material.shape.MaterialShapeDrawable;
import com.google.android.material.textfield.*;
import com.google.android.material.textview.MaterialTextView;
import java.io.*;
import java.text.DateFormat;
import java.util.*;
import java.util.concurrent.*;

public class MainActivity extends AppCompatActivity implements VoiceEngine.Listener {
 private final Handler main=new Handler(Looper.getMainLooper());
 private final ExecutorService disk=Executors.newSingleThreadExecutor();
 private VoiceEngine engine;
 private SessionStore store;
 private SessionStore.Session session;
 private SessionStore.Entry assistantEntry;
 private String state="starting",diagnostics="No reply timing measured.",actualBackend="";
 private int mode=1;
 private boolean speechAvailable=true;
 private boolean visible=true,hasFocus=false,switchingMode=false,followFeed=true;
 private TextView status,detail,live,timer,title,downloadLabel,setupTitle,setupBody,assistantBubble;
 private MaterialButton mic,send,install,play;
 private TextInputEditText input;
 private LinearLayout conversation,composer,recordingActions;
 private View intro,setup,suggestions;
 private ScrollView scroll;
 private LinearProgressIndicator progress;
 private LoadingIndicator thinking;
 private MaterialButtonToggleGroup modeButtons;
 private MediaPlayer player;
 private AudioManager audioManager;
 private AudioFocusRequest audioFocus;
 private SessionStore.Session exportSession;
 private String pendingReply="";
 private final String[] modes={"CPU · Compatibility","Adreno GPU · Recommended","Hexagon NPU · Experimental","GPU + NPU · Experimental"};
 private final Runnable saveLater=this::saveNow;
 private final Runnable renderReply=()->{if(assistantBubble!=null){assistantBubble.setText(pendingReply.isEmpty()?"…":pendingReply);scrollIfFollowing();}};

 @Override public void onCreate(Bundle saved){
  DynamicColors.applyToActivityIfAvailable(this);
  super.onCreate(saved);
  WindowCompat.setDecorFitsSystemWindows(getWindow(),false);
  mode=Math.max(0,Math.min(3,getPreferences(0).getBoolean("load_in_progress",false)?0:getPreferences(0).getInt("backend",1)));
  store=new SessionStore(this);
  String previous=getPreferences(0).getString("session","");
  try{session=previous.isEmpty()?new SessionStore.Session():store.read(previous);}catch(Exception ignored){session=new SessionStore.Session();}
  audioManager=(AudioManager)getSystemService(AUDIO_SERVICE);
  audioFocus=new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
   .setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
   .setOnAudioFocusChangeListener(change->{if(change<0){hasFocus=false;stopPlayback();if(state.equals("recording")){engine.finishListening();state("finishing","Saving your recording…");}else if(active()){engine.cancel();state("ready","Audio paused by another app.");}}}).build();
  bindViews();
  engine=new VoiceEngine(this,this);
  engine.fillers=getPreferences(0).getBoolean("fillers",true);
  engine.restoreHistory(session.entries);
  renderSession();
  engine.prepare(false,mode);
 }
 private int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
 private int color(int attr){return MaterialColors.getColor(this,attr,MainActivity.class.getSimpleName());}
 private TextView text(String words,int appearance){MaterialTextView t=new MaterialTextView(this);t.setText(words);t.setTextAppearance(appearance);return t;}
 private LinearLayout column(){LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);return box;}
 private void space(LinearLayout box,int amount){box.addView(new View(this),new LinearLayout.LayoutParams(1,dp(amount)));}
 private MaterialButton button(String words,int style){int layout=style==R.style.Widget_WalkieTalkie_Action?R.layout.button_action:style==com.google.android.material.R.style.Widget_Material3Expressive_Button_TonalButton?R.layout.button_tonal:R.layout.button_text;MaterialButton b=(MaterialButton)getLayoutInflater().inflate(layout,null);b.setText(words);return b;}
 private void surface(View view,int fill,int radius){MaterialShapeDrawable shape=new MaterialShapeDrawable();shape.setFillColor(ColorStateList.valueOf(color(fill)));shape.setCornerSize(dp(radius));view.setBackground(shape);}
 private boolean active(){return state.equals("arming")||state.equals("recording")||state.equals("listening")||state.equals("thinking")||state.equals("speaking")||state.equals("finishing");}
 private boolean editable(){return state.equals("ready")||state.equals("missing")||state.equals("error");}
 private boolean recordingMode(){return session.kind.equals("recording");}
 private void bindViews(){
  setContentView(R.layout.activity_main);
  View root=findViewById(R.id.window_root),content=findViewById(R.id.content);
  root.setOnApplyWindowInsetsListener((v,insets)->{android.graphics.Insets i=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.ime());v.setPadding(i.left,i.top,i.right,i.bottom);return insets;});
  root.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->{int width=Math.min(r-l-root.getPaddingLeft()-root.getPaddingRight(),dp(760));if(width>0&&content.getLayoutParams().width!=width){content.getLayoutParams().width=width;content.requestLayout();}});
  status=findViewById(R.id.status);detail=findViewById(R.id.detail);live=findViewById(R.id.live);
  timer=findViewById(R.id.timer);title=findViewById(R.id.session_title);downloadLabel=findViewById(R.id.download_label);
  setupTitle=findViewById(R.id.setup_title);setupBody=findViewById(R.id.setup_body);scroll=findViewById(R.id.scroll);
  mic=findViewById(R.id.mic);send=findViewById(R.id.send);install=findViewById(R.id.install);play=findViewById(R.id.play);input=findViewById(R.id.input);
  conversation=findViewById(R.id.conversation);composer=findViewById(R.id.composer);recordingActions=findViewById(R.id.recording_actions);
  intro=findViewById(R.id.intro);setup=findViewById(R.id.setup);suggestions=findViewById(R.id.suggestions);
  progress=findViewById(R.id.progress);thinking=findViewById(R.id.thinking);modeButtons=findViewById(R.id.modes);
  findViewById(R.id.settings).setOnClickListener(v->{if(editable())settings();else toast("Pause or finish the current turn first.");});
  findViewById(R.id.library).setOnClickListener(v->{if(editable())library();else toast("Pause or finish before opening your library.");});
  findViewById(R.id.new_chat).setOnClickListener(v->{if(editable())newSession(recordingMode()?"recording":"chat");else toast("Pause or finish the current turn first.");});
  modeButtons.addOnButtonCheckedListener((group,id,checked)->{if(!checked||switchingMode)return;String next=id==R.id.mode_record?"recording":"chat";if(!next.equals(session.kind)){if(editable())newSession(next);else updateMode();}});
  mic.setOnClickListener(v->{v.performHapticFeedback(HapticFeedbackConstants.CONFIRM);micAction();});
  install.setOnClickListener(v->{install.setEnabled(false);state("setup","Downloading voice models…");engine.prepare(true,mode);});
  send.setOnClickListener(v->sendText());
  input.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){send.setEnabled(state.equals("ready")&&!s.toString().trim().isEmpty());}public void afterTextChanged(Editable e){}});
  input.setOnEditorActionListener((v,id,event)->{if(id==EditorInfo.IME_ACTION_SEND){sendText();return true;}return false;});
  findViewById(R.id.suggest_unwind).setOnClickListener(v->{input.setText("Help me unwind and sort through my thoughts.");input.requestFocus();});
  findViewById(R.id.suggest_brainstorm).setOnClickListener(v->{input.setText("Help me brainstorm an idea. Ask me a question to get started.");input.requestFocus();});
  findViewById(R.id.summarize).setOnClickListener(v->reflect("Summarize this recording. Include the main points, decisions and next steps."));
  findViewById(R.id.reflect).setOnClickListener(v->ask());
  findViewById(R.id.export).setOnClickListener(v->export(session));
  play.setOnClickListener(v->playPause());
  scroll.setOnScrollChangeListener((v,x,y,oldX,oldY)->{if(y<oldY)followFeed=false;if(atBottom())followFeed=true;});
  updateMode();
 }
 private boolean atBottom(){return scroll.getChildCount()==0||scroll.getChildAt(0).getBottom()-scroll.getHeight()-scroll.getScrollY()<dp(80);}
 private void scrollIfFollowing(){if(followFeed)scroll.post(()->scroll.fullScroll(View.FOCUS_DOWN));}
 private void updateMode(){switchingMode=true;modeButtons.check(recordingMode()?R.id.mode_record:R.id.mode_chat);switchingMode=false;}
 private void newSession(String kind){speechAvailable=true;stopPlayback();saveNow();engine.clear();session=new SessionStore.Session();session.kind=kind;getPreferences(0).edit().remove("session").apply();assistantEntry=null;assistantBubble=null;input.setText("");hideKeyboard();renderSession();state(engine.ready()?"ready":"missing","A fresh space for your thoughts.");}
 private void renderSession(){
  conversation.removeAllViews();assistantBubble=null;assistantEntry=null;followFeed=true;
  int start=Math.max(0,session.entries.size()-120);
  if(start>0){MaterialButton older=button("Show all "+session.entries.size()+" transcript entries",com.google.android.material.R.style.Widget_Material3Expressive_Button_TextButton);conversation.addView(older);older.setOnClickListener(v->{conversation.removeView(older);for(int i=start-1;i>=0;i--){View row=entryCard(session.entries.get(i));conversation.addView(row,0);}});}
  for(int i=start;i<session.entries.size();i++)conversation.addView(entryCard(session.entries.get(i)));
  updateSessionViews();updateMode();scrollIfFollowing();
 }
 private void updateSessionViews(){
  boolean has=!session.entries.isEmpty()||session.durationMs>0;
  intro.setVisibility(!has&&state.equals("ready")?View.VISIBLE:View.GONE);
  title.setText(has?session.displayTitle():recordingMode()?"New recording":"New chat");title.setVisibility(View.VISIBLE);
  ((TextView)intro).setText(recordingMode()?"Tap Record to begin.":"Speak or type a thought.");
  findViewById(R.id.typing).setVisibility(recordingMode()?View.GONE:View.VISIBLE);
  timer.setText(SessionStore.stamp(session.durationMs));timer.setContentDescription("Recording duration "+SessionStore.stamp(session.durationMs));timer.setVisibility((recordingMode()||session.durationMs>0)&&(state.equals("ready")||active()||has)?View.VISIBLE:View.GONE);
  suggestions.setVisibility(!recordingMode()&&!has&&state.equals("ready")?View.VISIBLE:View.GONE);
  recordingActions.setVisibility(has&&!active()?View.VISIBLE:View.GONE);
  play.setVisibility(store.audio(session).length()>44?View.VISIBLE:View.GONE);
  findViewById(R.id.summarize).setEnabled(!session.transcript().isEmpty()&&state.equals("ready"));
  findViewById(R.id.reflect).setEnabled(!session.transcript().isEmpty()&&state.equals("ready"));
 }
 private View entryCard(SessionStore.Entry e){
  MaterialCardView card=new MaterialCardView(this);card.setRadius(dp(24));card.setStrokeWidth(0);card.setCardElevation(0);
  boolean assistant=e.role.equals("assistant");
  card.setCardBackgroundColor(color(assistant?com.google.android.material.R.attr.colorPrimaryContainer:com.google.android.material.R.attr.colorSurface));
  LinearLayout box=column();box.setPadding(dp(assistant?18:0),dp(8),dp(assistant?18:0),dp(16));card.addView(box);
  String label=e.role.equals("transcript")?SessionStore.stamp(e.startMs):assistant?"WalkieTalkie":"You";
  TextView who=text(label,com.google.android.material.R.style.TextAppearance_Material3_LabelMedium);who.setTextColor(color(assistant?com.google.android.material.R.attr.colorOnPrimaryContainer:com.google.android.material.R.attr.colorOnSurfaceVariant));box.addView(who);space(box,8);
  TextView words=text(e.text.isEmpty()?"…":e.text,com.google.android.material.R.style.TextAppearance_Material3_BodyLarge);words.setTextColor(color(assistant?com.google.android.material.R.attr.colorOnPrimaryContainer:com.google.android.material.R.attr.colorOnSurface));words.setTextIsSelectable(true);words.setLineSpacing(dp(3),1);box.addView(words);card.setTag(words);
  LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(10);card.setLayoutParams(lp);
  if(e.role.equals("transcript")){who.setContentDescription("Play audio from "+SessionStore.stamp(e.startMs));who.setMinHeight(dp(48));who.setOnClickListener(v->{if(!active())playFrom(e.startMs);});}
  return card;
 }
 private void append(SessionStore.Entry e){
  session.entries.add(e);if(session.title.trim().isEmpty()&&!e.text.trim().isEmpty())session.title=e.text.replaceAll("\s+"," ").substring(0,Math.min(60,e.text.replaceAll("\s+"," ").length()));
  session.updated=System.currentTimeMillis();View card=entryCard(e);conversation.addView(card);if(e.role.equals("assistant")){assistantEntry=e;assistantBubble=(TextView)card.getTag();}
  if(ValueAnimator.areAnimatorsEnabled()){card.setAlpha(0);card.animate().alpha(1).setDuration(180).start();}
  updateSessionViews();scrollIfFollowing();scheduleSave();
 }
 private void scheduleSave(){main.removeCallbacks(saveLater);main.postDelayed(saveLater,500);}
 private void saveNow(){
  main.removeCallbacks(saveLater);if(session==null||(session.entries.isEmpty()&&session.durationMs==0&&!store.audio(session).exists()))return;
  SessionStore.Session copy=session.snapshot();getPreferences(0).edit().putString("session",copy.id).apply();
  if(!disk.isShutdown())disk.execute(()->{try{store.save(copy);}catch(IOException error){main.post(()->{toast("Could not save: "+error.getMessage());if(state.equals("recording"))engine.finishListening();});}});
 }
 private boolean focus(){if(hasFocus)return true;try{hasFocus=audioManager.requestAudioFocus(audioFocus)==AudioManager.AUDIOFOCUS_REQUEST_GRANTED;}catch(RuntimeException e){hasFocus=false;}if(!hasFocus){detail.setText("Audio is busy. Try again.");detail.setVisibility(View.VISIBLE);}return hasFocus;}
 private void releaseFocus(){if(hasFocus){try{audioManager.abandonAudioFocusRequest(audioFocus);}catch(RuntimeException ignored){}hasFocus=false;}}
 private void micAction(){
  if(state.equals("recording")||state.equals("listening")){engine.finishListening();state("finishing","Saving this part of your recording…");return;}
  if(state.equals("thinking")||state.equals("speaking")){engine.cancel();saveNow();state("ready","Paused. Your recording and replies are saved.");return;}
  if(!state.equals("ready")||!engine.ready())return;
  stopPlayback();hideKeyboard();
  if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},8);return;}
  if(focus()){
   try{store.save(session);}catch(IOException error){error("Could not create recording: "+error.getMessage());return;}
   getPreferences(0).edit().putString("session",session.id).apply();
   Diagnostics.phase(this,"Record tapped · backend "+mode);
   speechAvailable=true;state("arming","Opening microphone…");
   if(recordingMode())engine.record(store.audio(session));else engine.listen(store.audio(session));
  }
 }
 private void sendText(){
  String value=input.getText()==null?"":input.getText().toString().trim();
  if(value.isEmpty()||!state.equals("ready")||!engine.ready())return;
  stopPlayback();if(!focus())return;input.setText("");hideKeyboard();state("thinking","Finding the words…");engine.send(value);
 }
 private void reflect(String instruction){if(!state.equals("ready")||!engine.ready()||session.transcript().isEmpty())return;stopPlayback();if(focus()){state("thinking","Reading your transcript…");engine.reflect(session.transcript(),instruction);}}
 private void hideKeyboard(){((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(input.getWindowToken(),0);input.clearFocus();}
 @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] result){super.onRequestPermissionsResult(request,permissions,result);if(request==8&&result.length>0&&result[0]==PackageManager.PERMISSION_GRANTED&&visible)micAction();else {detail.setText("Allow microphone access to record.");detail.setVisibility(View.VISIBLE);}}

 private BottomSheetDialog sheet(String heading,LinearLayout panel){
  BottomSheetDialog dialog=new BottomSheetDialog(this);ScrollView scrolling=new ScrollView(this);scrolling.setFillViewport(false);panel.setPadding(dp(24),dp(12),dp(24),dp(32));
  TextView label=text(heading,com.google.android.material.R.style.TextAppearance_Material3_HeadlineSmall_Emphasized);label.setAccessibilityHeading(true);label.setPadding(0,dp(8),0,dp(20));panel.addView(label,0);
  scrolling.addView(panel);dialog.setContentView(scrolling);dialog.setOnShowListener(d->{View frame=dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);if(frame!=null){frame.getLayoutParams().height=ViewGroup.LayoutParams.WRAP_CONTENT;BottomSheetBehavior<View> behavior=BottomSheetBehavior.from(frame);behavior.setMaxHeight((int)(getResources().getDisplayMetrics().heightPixels*.88));frame.requestLayout();behavior.setState(BottomSheetBehavior.STATE_EXPANDED);}});dialog.show();return dialog;
 }
 private void settings(){
  hideKeyboard();LinearLayout panel=column();
  int[] selected={mode};
  TextInputLayout engineBox=(TextInputLayout)getLayoutInflater().inflate(R.layout.dropdown,null);engineBox.setHint("Reply engine");
  MaterialAutoCompleteTextView choices=(MaterialAutoCompleteTextView)engineBox.getEditText();
  choices.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_list_item_1,modes));choices.setText(modes[mode],false);
  choices.setOnItemClickListener((parent,v,position,id)->selected[0]=position);panel.addView(engineBox);space(panel,12);
  MaterialSwitch fillers=new MaterialSwitch(this);fillers.setText("Natural replies");fillers.setMinHeight(dp(56));fillers.setChecked(engine.fillers);panel.addView(fillers);
  MaterialButton models=button("Models",com.google.android.material.R.style.Widget_Material3Expressive_Button_TonalButton);models.setId(R.id.models);panel.addView(models);
  MaterialButton about=button("About & privacy",com.google.android.material.R.style.Widget_Material3Expressive_Button_TextButton);panel.addView(about);
  about.setOnClickListener(v->new MaterialAlertDialogBuilder(this).setTitle("WalkieTalkie").setMessage("Audio and transcripts stay on this phone. Recording pauses when you leave the app. Speakers are not automatically identified.\n\nSpeech runs on CPU. Adreno accelerates replies; NPU modes need compatible OnePlus FastRPC support and remain experimental.\n\nVersion "+BuildConfig.VERSION_NAME).setPositiveButton("Done",null).show());
  MaterialButton copy=button("Copy diagnostics",com.google.android.material.R.style.Widget_Material3Expressive_Button_TextButton);panel.addView(copy);
  copy.setOnClickListener(v->{copy.setEnabled(false);String runtime=actualBackend+"\n"+engine.store.model.label()+"\n"+diagnostics;disk.execute(()->{
   String report=Diagnostics.describe(this)+"\n\n"+runtime;
   main.post(()->{if(isDestroyed())return;copy.setEnabled(true);((android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("WalkieTalkie diagnostics",report));toast("Diagnostics copied");});
  });});space(panel,16);
  MaterialButton apply=button("Done",R.style.Widget_WalkieTalkie_Action);panel.addView(apply);
  BottomSheetDialog dialog=sheet("Settings",panel);
  models.setOnClickListener(v->{dialog.dismiss();models();});
  apply.setOnClickListener(v->{int next=selected[0];engine.fillers=fillers.isChecked();getPreferences(0).edit().putInt("backend",next).putBoolean("fillers",engine.fillers).apply();boolean changed=mode!=next;mode=next;dialog.dismiss();if(changed||!engine.ready()){state("loading","Opening engine…");engine.prepare(false,mode);}});
 }
 private void ask(){
  TextInputEditText question=new TextInputEditText(this);question.setHint("Ask about this recording");question.setMinLines(2);question.setMaxLines(4);question.setPadding(dp(24),dp(12),dp(24),dp(12));
  new MaterialAlertDialogBuilder(this).setTitle("Ask WalkieTalkie").setView(question).setNeutralButton("Reflect with me",(d,w)->reflect("Reflect on this recording with me. Offer a useful perspective and one thoughtful follow-up question.")).setNegativeButton("Cancel",null).setPositiveButton("Ask",(d,w)->{String q=question.getText()==null?"":question.getText().toString().trim();if(!q.isEmpty())reflect(q);}).show();
 }
 private void models(){
  LinearLayout panel=column();panel.addView(text("Qwen models from Hugging Face. Choose speed or detail.",com.google.android.material.R.style.TextAppearance_Material3_BodyMedium));space(panel,16);
  BottomSheetDialog dialog=sheet("Models on your phone",panel);
  for(int i=0;i<ModelCatalog.MODELS.length;i++){
   int index=i;ModelCatalog.Model model=ModelCatalog.MODELS[i];LinearLayout card=column();card.setPadding(dp(16),dp(16),dp(16),dp(16));surface(card,com.google.android.material.R.attr.colorSurfaceContainer,24);
   card.addView(text(model.label(),com.google.android.material.R.style.TextAppearance_Material3_TitleMedium_Emphasized));space(card,6);
   card.addView(text(model.description()+"\n"+model.license()+" · Q4_0",com.google.android.material.R.style.TextAppearance_Material3_BodyMedium));
   boolean installed=engine.store.downloaded(i);MaterialButton action=button(i==engine.store.selected&&installed?"Selected":installed?"Use downloaded model":"Download from Hugging Face",com.google.android.material.R.style.Widget_Material3Expressive_Button_TonalButton);action.setEnabled(!(i==engine.store.selected&&installed));card.addView(action);
   action.setOnClickListener(v->{dialog.dismiss();state("setup","Preparing "+model.label()+"…");engine.selectModel(index,mode);});
   MaterialButton source=button("Model card ↗",com.google.android.material.R.style.Widget_Material3Expressive_Button_TextButton);source.setOnClickListener(v->startActivity(new Intent(Intent.ACTION_VIEW,android.net.Uri.parse("https://huggingface.co/"+model.repo()))));card.addView(source);panel.addView(card);space(panel,12);
  }
 }
 private void library(){
  stopPlayback();hideKeyboard();LinearLayout panel=column();
  TextInputLayout searchBox=new TextInputLayout(this);searchBox.setHint("Search titles and transcripts");TextInputEditText search=new TextInputEditText(searchBox.getContext());search.setSingleLine(true);searchBox.addView(search);panel.addView(searchBox);space(panel,12);
  String[] filters={"All","Recordings","Conversations"},sorts={"Recently updated","Oldest first","Title A–Z","Longest recording"};
  int[] selection={0,Math.max(0,Math.min(3,getPreferences(0).getInt("sort",0)))};
  TextInputLayout filterBox=(TextInputLayout)getLayoutInflater().inflate(R.layout.dropdown,null);filterBox.setHint("Show");MaterialAutoCompleteTextView filter=(MaterialAutoCompleteTextView)filterBox.getEditText();filter.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_list_item_1,filters));filter.setText(filters[0],false);panel.addView(filterBox);space(panel,12);
  TextInputLayout sortBox=(TextInputLayout)getLayoutInflater().inflate(R.layout.dropdown,null);sortBox.setHint("Sort by");MaterialAutoCompleteTextView sort=(MaterialAutoCompleteTextView)sortBox.getEditText();sort.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_list_item_1,sorts));sort.setText(sorts[selection[1]],false);panel.addView(sortBox);space(panel,12);
  LinearLayout results=column();panel.addView(results);TextView loading=text("Opening your library…",com.google.android.material.R.style.TextAppearance_Material3_BodyLarge);results.addView(loading);
  BottomSheetDialog dialog=sheet("Library",panel);saveNow();
  List<SessionStore.Session> cached=new ArrayList<>();
  Runnable render=()->{results.removeAllViews();if(store.unreadableCount>0)results.addView(text(store.unreadableCount+" recording(s) could not be opened. Their files remain on this phone.",com.google.android.material.R.style.TextAppearance_Material3_BodyMedium));String kind=switch(selection[0]){case 1->"recording";case 2->"chat";default->"all";};List<SessionStore.Session> items=SessionStore.sorted(cached,search.getText()==null?"":search.getText().toString(),kind,selection[1]);if(items.isEmpty())results.addView(text("No recordings yet.",com.google.android.material.R.style.TextAppearance_Material3_BodyLarge));
   for(SessionStore.Session item:items){
    LinearLayout row=column();row.setPadding(dp(16),dp(16),dp(16),dp(16));surface(row,com.google.android.material.R.attr.colorSurfaceContainer,24);
    MaterialButton open=button((item.pinned?"★  ":"")+item.displayTitle(),com.google.android.material.R.style.Widget_Material3Expressive_Button_TextButton);open.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);open.setMaxLines(2);LinearLayout heading=new LinearLayout(this);heading.setGravity(Gravity.CENTER_VERTICAL);heading.addView(open,new LinearLayout.LayoutParams(0,-2,1));row.addView(heading);
    row.addView(text((item.kind.equals("recording")?"Recording":"Conversation")+" · "+SessionStore.stamp(item.durationMs)+"\n"+DateFormat.getDateTimeInstance(DateFormat.MEDIUM,DateFormat.SHORT).format(new Date(item.updated)),com.google.android.material.R.style.TextAppearance_Material3_BodySmall));
    MaterialButton more=button("•••",com.google.android.material.R.style.Widget_Material3Expressive_Button_TextButton);more.setContentDescription("Recording options");more.setMinWidth(dp(48));more.setPadding(0,0,0,0);heading.addView(more,new LinearLayout.LayoutParams(dp(48),dp(48)));panel.setFocusableInTouchMode(true);
    open.setOnClickListener(v->{dialog.dismiss();speechAvailable=true;session=item;engine.restoreHistory(item.entries);getPreferences(0).edit().putString("session",item.id).apply();renderSession();state(engine.ready()?"ready":"missing","Saved on this phone.");});
    more.setOnClickListener(v->{dialog.dismiss();sessionMenu(item);});results.addView(row);space(results,10);
   }
  };
  filter.setOnItemClickListener((parent,v,position,id)->{selection[0]=position;render.run();});
  sort.setOnItemClickListener((parent,v,position,id)->{selection[1]=position;getPreferences(0).edit().putInt("sort",position).apply();render.run();});
  search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){render.run();}public void afterTextChanged(Editable e){}});
  disk.execute(()->{try{List<SessionStore.Session> items=store.list();main.post(()->{cached.addAll(items);render.run();});}catch(IOException error){main.post(()->{results.removeAllViews();results.addView(text("Could not open library: "+error.getMessage(),com.google.android.material.R.style.TextAppearance_Material3_BodyMedium));});}});
 }
 private void sessionMenu(SessionStore.Session item){
  new MaterialAlertDialogBuilder(this).setTitle(item.displayTitle()).setItems(new String[]{"Rename",item.pinned?"Unpin":"Pin to top","Export","Delete audio and transcript"},(d,which)->{
   if(which==0){EditText name=new TextInputEditText(this);name.setText(item.displayTitle());name.setSingleLine(true);name.setPadding(dp(24),dp(12),dp(24),dp(12));new MaterialAlertDialogBuilder(this).setTitle("Name this recording").setView(name).setNegativeButton("Cancel",null).setPositiveButton("Save",(a,b)->{String value=name.getText().toString().trim();if(!value.isEmpty()){item.title=value;if(item.id.equals(session.id))session.title=value;saveItem(item);updateSessionViews();}}).show();}
   else if(which==1){item.pinned=!item.pinned;if(item.id.equals(session.id))session.pinned=item.pinned;saveItem(item);}
   else if(which==2)export(item);
   else new MaterialAlertDialogBuilder(this).setTitle("Delete this recording?").setMessage("Its audio, transcript and replies will be removed from this phone.").setNegativeButton("Keep",null).setPositiveButton("Delete",(a,b)->{stopPlayback();disk.execute(()->{try{store.delete(item);main.post(()->{if(item.id.equals(session.id)){session=new SessionStore.Session();getPreferences(0).edit().remove("session").apply();engine.clear();renderSession();}toast("Deleted");});}catch(IOException error){main.post(()->toast(error.getMessage()));}});}).show();
  }).show();
 }
 private void saveItem(SessionStore.Session item){SessionStore.Session copy=item.snapshot();disk.execute(()->{try{store.save(copy);main.post(()->toast("Saved"));}catch(IOException e){main.post(()->toast(e.getMessage()));}});}
 private void export(SessionStore.Session item){exportSession=item.snapshot();new MaterialAlertDialogBuilder(this).setTitle("Export").setItems(new String[]{"Transcript (.txt)","Recording audio (.wav)"},(d,which)->{if(which==1&&!store.audio(item).exists()){toast("This conversation has no recorded audio.");return;}Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType(which==0?"text/plain":"audio/wav").putExtra(Intent.EXTRA_TITLE,item.displayTitle().replaceAll("[^a-zA-Z0-9 _-]","").trim()+(which==0?".txt":".wav"));startActivityForResult(intent,which==0?51:52);}).show();}
 @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if((request==51||request==52)&&result==RESULT_OK&&data!=null&&data.getData()!=null&&exportSession!=null){SessionStore.Session copy=exportSession;android.net.Uri uri=data.getData();disk.execute(()->{try(OutputStream out=getContentResolver().openOutputStream(uri)){if(out==null)throw new IOException("Cannot open destination");if(request==51)out.write(copy.exportText().getBytes(java.nio.charset.StandardCharsets.UTF_8));else try(InputStream in=new FileInputStream(store.audio(copy))){byte[] buffer=new byte[65536];int count;while((count=in.read(buffer))!=-1)out.write(buffer,0,count);}main.post(()->toast("Export saved"));}catch(IOException error){main.post(()->toast("Export failed: "+error.getMessage()));}});}}
 private void playPause(){if(player!=null){stopPlayback();releaseFocus();}else playFrom(0);}
 private void playFrom(long position){
  if(active()||!store.audio(session).exists())return;stopPlayback();if(!focus())return;
  try{player=new MediaPlayer();player.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build());player.setDataSource(store.audio(session).getAbsolutePath());player.setOnPreparedListener(p->{if(p!=player||isDestroyed())return;p.seekTo(position,MediaPlayer.SEEK_CLOSEST);p.start();play.setText("Stop");});player.setOnCompletionListener(p->{stopPlayback();releaseFocus();});player.setOnErrorListener((p,w,e)->{stopPlayback();releaseFocus();toast("Could not play this recording.");return true;});player.prepareAsync();}catch(IOException|RuntimeException error){stopPlayback();releaseFocus();toast(error.getMessage());}
 }
 private void stopPlayback(){if(player!=null){player.release();player=null;}if(play!=null)play.setText("Play");}
 private void toast(String message){if(!isDestroyed())Toast.makeText(this,message,Toast.LENGTH_LONG).show();}
 @Override public void state(String value,String message){runOnUiThread(()->{
  if(isDestroyed())return;String previous=state;state=value;detail.setVisibility(View.GONE);
  if(value.equals("loading"))getPreferences(0).edit().putBoolean("load_in_progress",true).apply();
  if(value.equals("ready")||value.equals("error"))getPreferences(0).edit().putBoolean("load_in_progress",false).apply();
  status.setText(switch(value){case "ready"->session.durationMs>0?"Paused":"Ready";case "recording"->"● Recording";case "listening"->"Listening";case "thinking"->"Thinking";case "speaking"->"Speaking";case "setup"->"Downloading";case "missing"->"Setup";case "error"->"Error";case "finishing"->"Saving";default->"Opening…";});
  boolean ready=value.equals("ready"),busy=value.equals("setup")||value.equals("loading")||value.equals("starting");
  mic.setEnabled(ready||value.equals("recording")||value.equals("listening")||value.equals("thinking")||value.equals("speaking"));
  mic.setText(value.equals("recording")?"Pause":value.equals("listening")?"Done":value.equals("thinking")||value.equals("speaking")?"Interrupt":recordingMode()?(session.durationMs>0?"Resume":"Record"):"Speak");
  mic.setIconResource(active()?R.drawable.ic_stop:R.drawable.ic_mic);
  send.setEnabled(ready&&input.getText()!=null&&!input.getText().toString().trim().isEmpty());input.setEnabled(ready);
  modeButtons.setEnabled(editable());for(int i=0;i<modeButtons.getChildCount();i++)modeButtons.getChildAt(i).setEnabled(editable());
  setup.setVisibility(value.equals("missing")||value.equals("setup")||value.equals("error")||value.equals("loading")?View.VISIBLE:View.GONE);
  progress.setVisibility(busy?View.VISIBLE:View.GONE);progress.setIndeterminate(value.equals("loading"));
  install.setVisibility(busy?View.GONE:View.VISIBLE);install.setEnabled(value.equals("missing")||value.equals("error"));install.setText(value.equals("error")?"Retry":"Download");
  setupTitle.setText(value.equals("error")?"Couldn’t continue":value.equals("loading")?"Opening models…":"Offline models");
  setupBody.setText(value.equals("error")?message:value.equals("loading")?"Preparing speech recognition.":getString(R.string.setup_body));
  composer.setVisibility(ready||active()?View.VISIBLE:View.GONE);thinking.setVisibility(value.equals("thinking")?View.VISIBLE:View.GONE);
  if(busy||active())getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
  if(!active()){live.setText("");live.setVisibility(View.GONE);if(player==null)releaseFocus();}
  updateSessionViews();showSpeechWarning();
  if(ready&&!previous.equals("ready")){saveNow();if(assistantBubble!=null)assistantBubble.announceForAccessibility("WalkieTalkie’s reply is ready.");}
 });}
 private void showSpeechWarning(){
  if(!speechAvailable&&(state.equals("recording")||state.equals("listening")||state.equals("ready"))){
   detail.setText(active()?"Transcription paused. Audio is still recording.":"Audio saved. Transcription unavailable.");detail.setVisibility(View.VISIBLE);
  }
 }
 @Override public void speechStatus(boolean available,String reason){main.post(()->{if(isDestroyed())return;speechAvailable=available;showSpeechWarning();});}
 @Override public void progress(String label,int percent){main.post(()->{if(isDestroyed())return;downloadLabel.setVisibility(View.VISIBLE);downloadLabel.setText(label);progress.setProgressCompat(Math.max(0,Math.min(100,percent)),true);});}
 @Override public void partial(String words){main.post(()->{if(isDestroyed()||isFinishing())return;if(state.equals("recording")||state.equals("listening")||state.equals("finishing")){live.setText(words);live.setVisibility(words==null||words.trim().isEmpty()?View.GONE:View.VISIBLE);scrollIfFollowing();}});}
 @Override public void segment(String words,long start,long end){main.post(()->{if(isDestroyed()||isFinishing())return;append(new SessionStore.Entry("transcript",words,start,end));session.durationMs=Math.max(session.durationMs,end);});}
 @Override public void elapsed(long duration){main.post(()->{if(isDestroyed()||isFinishing())return;session.durationMs=duration;timer.setText(SessionStore.stamp(duration));timer.setContentDescription("Recording duration "+SessionStore.stamp(duration));if(duration%5000<300)scheduleSave();});}
 @Override public void user(String words){main.post(()->{if(isDestroyed()||isFinishing())return;append(new SessionStore.Entry("user",words,session.durationMs,session.durationMs));assistantBubble=null;assistantEntry=null;live.setText("");live.setVisibility(View.GONE);});}
 @Override public void reply(String words){main.post(()->{if(isDestroyed()||isFinishing())return;if(assistantEntry==null)append(new SessionStore.Entry("assistant",words,session.durationMs,session.durationMs));else assistantEntry.text=words;pendingReply=words;main.removeCallbacks(renderReply);main.postDelayed(renderReply,40);});}
 @Override public void backend(String value){main.post(()->{if(isDestroyed()||isFinishing())return;actualBackend=value;});}
 @Override public void metrics(String value){main.post(()->diagnostics=value);}
 @Override public void error(String message){state("error",message);main.post(()->{if(isDestroyed()||isFinishing())return;saveNow();downloadLabel.setText("Your saved recordings remain in the library.");});}
 @Override protected void onStop(){visible=false;stopPlayback();if(engine!=null){if(state.equals("recording")||state.equals("arming")){engine.finishListening();state("finishing","Saving before leaving the app…");}else if(active()){engine.cancel();state("ready","Paused. Your words are saved.");}}saveNow();releaseFocus();super.onStop();}
 @Override protected void onStart(){super.onStart();visible=true;}
 @Override protected void onDestroy(){saveNow();main.removeCallbacksAndMessages(null);if(engine!=null)engine.close();disk.shutdown();super.onDestroy();}
}
