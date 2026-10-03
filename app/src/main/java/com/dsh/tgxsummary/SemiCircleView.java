package com.dsh.tgxsummary;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

/**
 * Collapsed handle: a half round tab hugging a screen edge.
 * The curved side faces into the screen (a right edge means the flat side is on the right).
 */
public class SemiCircleView extends View {

    private static final long PULSE_PERIOD_MS = 600L;
    private static final int PULSE_MIN_ALPHA = 150;
    private static final int PULSE_MAX_ALPHA = 255;

    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path shapePath = new Path();
    private final RectF oval = new RectF();

    private int edge = Gravity.RIGHT;
    private boolean busy = false;
    private boolean pressed = false;
    private boolean moved = false;
    private float downX = 0f;
    private float downY = 0f;
    private int touchSlop;
    private Runnable tapListener;

    public SemiCircleView(Context context) {
        super(context);
        setWillNotDraw(false);
        setClickable(true);
        setFocusable(true);
        setContentDescription("AI");
        int slop = ViewConfiguration.get(context).getScaledTouchSlop();
        touchSlop = slop > 0 ? slop : Md3.dp(context, 8);
        textPaint.setTypeface(Typeface.DEFAULT_BOLD);
        textPaint.setTextSize(Md3.sp(context, 12f));
        textPaint.setTextAlign(Paint.Align.CENTER);
        setElevation(Md3.dp(context, 6f));
    }

    public void setEdge(int edge) {
        if (edge == Gravity.LEFT || edge == Gravity.RIGHT) {
            if (this.edge != edge) {
                this.edge = edge;
                invalidate();
            }
        }
    }

    public int getEdge() {
        return edge;
    }

    public void setBusy(boolean busy) {
        if (this.busy != busy) {
            this.busy = busy;
            invalidate();
        }
    }

    public void setOnTapListener(Runnable r) {
        tapListener = r;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int defaultWidth = Md3.dp(getContext(), 30f);
        int defaultHeight = Md3.dp(getContext(), 64f);
        int width = measureDimension(defaultWidth, widthMeasureSpec);
        int height = measureDimension(defaultHeight, heightMeasureSpec);
        setMeasuredDimension(width, height);
    }

    private int measureDimension(int fallback, int measureSpec) {
        int mode = MeasureSpec.getMode(measureSpec);
        int size = MeasureSpec.getSize(measureSpec);
        if (mode == MeasureSpec.EXACTLY) {
            return size;
        }
        if (mode == MeasureSpec.AT_MOST) {
            return Math.min(size, fallback);
        }
        return fallback;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int width = getWidth();
        int height = getHeight();
        if (width <= 0 || height <= 0) {
            return;
        }
        shapePath.reset();
        if (edge == Gravity.LEFT) {
            oval.set(-width, 0f, width, height);
            shapePath.addArc(oval, 270f, 180f);
        } else {
            oval.set(0f, 0f, width * 2f, height);
            shapePath.addArc(oval, 90f, 180f);
        }
        shapePath.close();

        int alpha = currentAlpha();
        fillPaint.setStyle(Paint.Style.FILL);
        fillPaint.setColor(Md3.primary(getContext()));
        fillPaint.setAlpha(alpha);
        canvas.drawPath(shapePath, fillPaint);

        textPaint.setColor(Md3.onPrimary(getContext()));
        textPaint.setAlpha(alpha);
        Paint.FontMetrics metrics = textPaint.getFontMetrics();
        float baseline = height / 2f - (metrics.ascent + metrics.descent) / 2f;
        canvas.drawText("AI", width / 2f, baseline, textPaint);

        if (busy) {
            postInvalidateOnAnimation();
        }
    }

    private int currentAlpha() {
        int alpha = PULSE_MAX_ALPHA;
        if (busy) {
            long position = System.currentTimeMillis() % PULSE_PERIOD_MS;
            float phase = (float) position / (float) PULSE_PERIOD_MS;
            float triangle = phase < 0.5f ? phase * 2f : 2f - phase * 2f;
            int range = PULSE_MAX_ALPHA - PULSE_MIN_ALPHA;
            alpha = PULSE_MIN_ALPHA + Math.round(range * triangle);
        }
        if (pressed && !busy) {
            alpha = Math.min(alpha, 200);
        }
        return alpha;
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            // An external OnTouchListener (for example edge dragging) may consume the end of the
            // gesture, so clear the pressed state here instead of relying on onTouchEvent only.
            if (pressed) {
                pressed = false;
                invalidate();
            }
            if (action == MotionEvent.ACTION_CANCEL) {
                moved = false;
            }
        }
        return super.dispatchTouchEvent(event);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            downX = event.getX();
            downY = event.getY();
            moved = false;
            pressed = true;
            invalidate();
            return true;
        }
        if (action == MotionEvent.ACTION_MOVE) {
            if (!moved) {
                float dx = event.getX() - downX;
                float dy = event.getY() - downY;
                if (dx * dx + dy * dy > (float) touchSlop * (float) touchSlop) {
                    moved = true;
                }
            }
            return true;
        }
        if (action == MotionEvent.ACTION_UP) {
            pressed = false;
            invalidate();
            if (!moved) {
                performClick();
            }
            return true;
        }
        if (action == MotionEvent.ACTION_CANCEL) {
            pressed = false;
            moved = false;
            invalidate();
            return true;
        }
        return super.onTouchEvent(event);
    }

    @Override
    public boolean performClick() {
        super.performClick();
        Runnable listener = tapListener;
        if (listener != null) {
            listener.run();
        }
        return true;
    }
}
