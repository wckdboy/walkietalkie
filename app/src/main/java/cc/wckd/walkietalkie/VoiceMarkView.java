package cc.wckd.walkietalkie;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;
import com.google.android.material.color.MaterialColors;

/** Decorative voice mark. It intentionally does not pretend to measure audio. */
public final class VoiceMarkView extends View {
 private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
 public VoiceMarkView(Context context, AttributeSet attrs) { super(context, attrs); }
 @Override protected void onDraw(Canvas canvas) {
  super.onDraw(canvas);
  float unit = Math.min(getWidth(), getHeight()) / 112f;
  canvas.save();
  canvas.translate(getWidth()/2f, getHeight()/2f);
  canvas.scale(unit, unit);
  paint.setColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorPrimaryContainer));
  for (int i=0;i<8;i++) { canvas.save();canvas.rotate(i*45);canvas.drawRoundRect(-24,-53,24,28,24,24,paint);canvas.restore(); }
  paint.setColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnPrimaryContainer));
  float[] heights={16,30,44,30,16};
  for(int i=0;i<heights.length;i++) {float x=(i-2)*10;canvas.drawRoundRect(x-3,-heights[i]/2,x+3,heights[i]/2,3,3,paint);}
  canvas.restore();
 }
}
