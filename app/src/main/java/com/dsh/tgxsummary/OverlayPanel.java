package com.dsh.tgxsummary;

import android.content.Context;
import android.graphics.Typeface;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * Material 3 style summary card: header, chat title, count stepper, detail level segments,
 * generate button, status row and a scrollable result area.
 */
public class OverlayPanel extends LinearLayout {

    public interface Callback {
        void onGenerate(int count, String level);
        void onCollapse();
    }

    private static final int MIN_COUNT = 1;
    private static final int MAX_COUNT = 500;
    private static final int PANEL_WIDTH_DP = 300;
    private static final int PANEL_MAX_HEIGHT_DP = 460;
    private static final int RESULT_AREA_HEIGHT_DP = 200;
    private static final int RESULT_AREA_MIN_HEIGHT_DP = 120;
    private static final String LEVEL_SIMPLE = "l";
    private static final String LEVEL_STANDARD = "m";
    private static final String LEVEL_DETAILED = "h";
    private static final String TITLE_UNKNOWN = "未识别当前会话";
    private static final String RESULT_PLACEHOLDER = "点击「生成总结」开始";

    private final Callback callback;
    private final TextView chatTitleView;
    private final TextView minusView;
    private final EditText countInput;
    private final TextView plusView;
    private final TextView[] segmentViews = new TextView[3];
    private final String[] segmentLevels = {LEVEL_SIMPLE, LEVEL_STANDARD, LEVEL_DETAILED};
    private final TextView generateView;
    private final LinearLayout statusRow;
    private final ProgressBar progressView;
    private final TextView statusView;
    private final TextView resultView;

    private int count = 50;
    private String level = LEVEL_STANDARD;
    private boolean busy = false;

    public OverlayPanel(Context context, Callback callback) {
        super(context);
        this.callback = callback;
        setOrientation(VERTICAL);
        int padding = Md3.dp(context, 16f);
        setPadding(padding, padding, padding, padding);
        setBackground(Md3.rounded(context, Md3.surface(context), 28f));
        setElevation(Md3.dp(context, 12f));
        setLayoutParams(new LinearLayout.LayoutParams(
                Md3.dp(context, PANEL_WIDTH_DP), ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout header = buildHeader(context);
        addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        chatTitleView = new TextView(context);
        chatTitleView.setText(TITLE_UNKNOWN);
        chatTitleView.setTextSize(13f);
        chatTitleView.setTextColor(Md3.onSurfaceVariant(context));
        chatTitleView.setSingleLine(true);
        chatTitleView.setEllipsize(TextUtils.TruncateAt.END);
        addView(chatTitleView, rowParams(8));

        LinearLayout stepper = new LinearLayout(context);
        stepper.setOrientation(HORIZONTAL);
        stepper.setGravity(Gravity.CENTER_VERTICAL);

        TextView countLabel = new TextView(context);
        countLabel.setText("总结条数");
        countLabel.setTextSize(13f);
        countLabel.setTextColor(Md3.onSurfaceVariant(context));
        stepper.addView(countLabel, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        View stepperSpacer = new View(context);
        stepper.addView(stepperSpacer, new LinearLayout.LayoutParams(0, 1, 1f));

        int square = Md3.dp(context, 40f);
        minusView = new TextView(context);
        minusView.setText("-");
        minusView.setTextSize(20f);
        minusView.setTextColor(Md3.onPrimaryContainer(context));
        minusView.setTypeface(Typeface.DEFAULT_BOLD);
        minusView.setGravity(Gravity.CENTER);
        minusView.setBackground(Md3.rounded(context, Md3.primaryContainer(context), 20f));
        minusView.setClickable(true);
        minusView.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                changeCount(-1);
                if (OverlayPanel.this.callback != null) {
                    OverlayPanel.this.callback.onGenerate(getCount(), getLevel());
                }
            }
        });
        stepper.addView(minusView, new LinearLayout.LayoutParams(square, square));

        countInput = new EditText(context);
        countInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        countInput.setText(String.valueOf(count));
        countInput.setTextSize(16f);
        countInput.setTextColor(Md3.onSurface(context));
        countInput.setGravity(Gravity.CENTER);
        countInput.setSingleLine(true);
        countInput.setBackground(Md3.rounded(context, Md3.surfaceContainer(context), 12f));
        int inputPad = Md3.dp(context, 4f);
        countInput.setPadding(inputPad, inputPad, inputPad, inputPad);
        countInput.setFilters(new InputFilter[] { new InputFilter.LengthFilter(3) });
        countInput.setOnFocusChangeListener(new OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (!hasFocus) {
                    commitCountFromInput();
                }
            }
        });
        countInput.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, android.view.KeyEvent event) {
                commitCountFromInput();
                return false;
            }
        });
        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(
                Md3.dp(context, 56f), square);
        inputParams.leftMargin = Md3.dp(context, 8f);
        inputParams.rightMargin = Md3.dp(context, 8f);
        stepper.addView(countInput, inputParams);

        plusView = new TextView(context);
        plusView.setText("+");
        plusView.setTextSize(20f);
        plusView.setTextColor(Md3.onPrimaryContainer(context));
        plusView.setTypeface(Typeface.DEFAULT_BOLD);
        plusView.setGravity(Gravity.CENTER);
        plusView.setBackground(Md3.rounded(context, Md3.primaryContainer(context), 20f));
        plusView.setClickable(true);
        plusView.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                changeCount(1);
                if (OverlayPanel.this.callback != null) {
                    OverlayPanel.this.callback.onGenerate(getCount(), getLevel());
                }
            }
        });
        stepper.addView(plusView, new LinearLayout.LayoutParams(square, square));
        addView(stepper, rowParams(10));

        LinearLayout segmentRow = new LinearLayout(context);
        segmentRow.setOrientation(HORIZONTAL);
        segmentRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView levelLabel = new TextView(context);
        levelLabel.setText("详细程度");
        levelLabel.setTextSize(13f);
        levelLabel.setTextColor(Md3.onSurfaceVariant(context));
        segmentRow.addView(levelLabel, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout segmentGroup = new LinearLayout(context);
        segmentGroup.setOrientation(HORIZONTAL);
        segmentGroup.setGravity(Gravity.CENTER_VERTICAL);
        segmentGroup.setBackground(Md3.rounded(context, Md3.surfaceContainer(context), 20f));
        String[] labels = {"简洁", "标准", "详细"};
        for (int i = 0; i < labels.length; i++) {
            final int index = i;
            TextView segment = new TextView(context);
            segment.setText(labels[i]);
            segment.setTextSize(13f);
            segment.setGravity(Gravity.CENTER);
            segment.setClickable(true);
            segment.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    level = segmentLevels[index];
                    updateSegments();
                    if (OverlayPanel.this.callback != null) {
                        OverlayPanel.this.callback.onGenerate(getCount(), getLevel());
                    }
                }
            });
            segmentViews[i] = segment;
            segmentGroup.addView(segment, new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        }
        LinearLayout.LayoutParams groupParams = new LinearLayout.LayoutParams(
                0, Md3.dp(context, 40f), 1f);
        groupParams.leftMargin = Md3.dp(context, 8f);
        segmentRow.addView(segmentGroup, groupParams);
        addView(segmentRow, rowParams(10));

        generateView = new TextView(context);
        generateView.setText("生成总结");
        generateView.setTextSize(15f);
        generateView.setTextColor(Md3.onPrimary(context));
        generateView.setTypeface(Typeface.DEFAULT_BOLD);
        generateView.setGravity(Gravity.CENTER);
        generateView.setBackground(Md3.rounded(context, Md3.primary(context), 22f));
        generateView.setClickable(true);
        generateView.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (busy) {
                    return;
                }
                commitCountFromInput();
                if (OverlayPanel.this.callback != null) {
                    OverlayPanel.this.callback.onGenerate(getCount(), getLevel());
                }
            }
        });
        LinearLayout.LayoutParams generateParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Md3.dp(context, 44f));
        generateParams.topMargin = Md3.dp(context, 14f);
        addView(generateView, generateParams);

        statusRow = new LinearLayout(context);
        statusRow.setOrientation(HORIZONTAL);
        statusRow.setGravity(Gravity.CENTER_VERTICAL);
        statusRow.setVisibility(View.GONE);

        progressView = new ProgressBar(context, null, android.R.attr.progressBarStyleSmall);
        progressView.setVisibility(View.GONE);
        statusRow.addView(progressView, new LinearLayout.LayoutParams(
                Md3.dp(context, 20f), Md3.dp(context, 20f)));

        statusView = new TextView(context);
        statusView.setTextSize(12f);
        statusView.setTextColor(Md3.onSurfaceVariant(context));
        statusView.setVisibility(View.GONE);
        LinearLayout.LayoutParams statusTextParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        statusTextParams.leftMargin = Md3.dp(context, 8f);
        statusRow.addView(statusView, statusTextParams);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        statusParams.topMargin = Md3.dp(context, 10f);
        addView(statusRow, statusParams);

        LinearLayout resultContainer = new LinearLayout(context);
        resultContainer.setOrientation(VERTICAL);
        resultContainer.setBackground(Md3.rounded(context, Md3.surfaceContainer(context), 12f));
        int resultPadding = Md3.dp(context, 12f);
        resultContainer.setPadding(resultPadding, resultPadding, resultPadding, resultPadding);

        ScrollView scroll = new ScrollView(context);
        resultView = new TextView(context);
        resultView.setText(RESULT_PLACEHOLDER);
        resultView.setTextSize(14f);
        resultView.setTextColor(Md3.onSurface(context));
        resultView.setLineSpacing(0f, 1.3f);
        resultView.setTextIsSelectable(true);
        scroll.addView(resultView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        scroll.setMinimumHeight(Md3.dp(context, RESULT_AREA_MIN_HEIGHT_DP));
        // 注意：resultContainer 是竖向 LinearLayout，weight 作用于高度，
        // 所以必须是 (width=MATCH_PARENT, height=0, weight=1)，否则宽度为 0、文字不可见。
        resultContainer.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        LinearLayout.LayoutParams resultParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Md3.dp(context, RESULT_AREA_HEIGHT_DP));
        resultParams.topMargin = Md3.dp(context, 12f);
        addView(resultContainer, resultParams);

        updateSegments();
    }

    private LinearLayout buildHeader(Context context) {
        LinearLayout header = new LinearLayout(context);
        header.setOrientation(HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = new TextView(context);
        title.setText("消息总结");
        title.setTextSize(18f);
        title.setTextColor(Md3.onSurface(context));
        title.setTypeface(Typeface.DEFAULT_BOLD);
        header.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        View spacer = new View(context);
        header.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1f));

        TextView collapse = new TextView(context);
        collapse.setText("▾");
        collapse.setTextSize(20f);
        collapse.setTextColor(Md3.onSurfaceVariant(context));
        collapse.setGravity(Gravity.CENTER);
        collapse.setContentDescription("收起");
        collapse.setClickable(true);
        collapse.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (OverlayPanel.this.callback != null) {
                    OverlayPanel.this.callback.onCollapse();
                }
            }
        });
        int square = Md3.dp(context, 40f);
        header.addView(collapse, new LinearLayout.LayoutParams(square, square));
        return header;
    }

    private LinearLayout.LayoutParams rowParams(int topMarginDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = Md3.dp(getContext(), topMarginDp);
        return params;
    }

    private void changeCount(int delta) {
        commitCountFromInput();
        int value = count + delta;
        if (value < MIN_COUNT) {
            value = MIN_COUNT;
        }
        if (value > MAX_COUNT) {
            value = MAX_COUNT;
        }
        count = value;
        applyCountToInput();
    }

    private void applyCountToInput() {
        String text = String.valueOf(count);
        if (!text.equals(countInput.getText().toString())) {
            countInput.setText(text);
            countInput.setSelection(text.length());
        }
    }

    private void commitCountFromInput() {
        int value = count;
        String text = countInput.getText().toString().trim();
        if (text.length() > 0) {
            try {
                value = Integer.parseInt(text);
            } catch (NumberFormatException e) {
                value = count;
            }
        }
        if (value < MIN_COUNT) {
            value = MIN_COUNT;
        }
        if (value > MAX_COUNT) {
            value = MAX_COUNT;
        }
        count = value;
        applyCountToInput();
    }

    private void updateSegments() {
        Context context = getContext();
        for (int i = 0; i < segmentViews.length; i++) {
            TextView segment = segmentViews[i];
            if (segment == null) {
                continue;
            }
            if (segmentLevels[i].equals(level)) {
                segment.setBackground(Md3.rounded(context, Md3.primaryContainer(context), 20f));
                segment.setTextColor(Md3.onPrimaryContainer(context));
                segment.setTypeface(Typeface.DEFAULT_BOLD);
            } else {
                segment.setBackground(null);
                segment.setTextColor(Md3.onSurfaceVariant(context));
                segment.setTypeface(Typeface.DEFAULT);
            }
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        if (widthMode != MeasureSpec.EXACTLY) {
            int wanted = Md3.dp(getContext(), PANEL_WIDTH_DP);
            if (widthMode == MeasureSpec.AT_MOST) {
                wanted = Math.min(wanted, MeasureSpec.getSize(widthMeasureSpec));
                widthMeasureSpec = MeasureSpec.makeMeasureSpec(wanted, MeasureSpec.AT_MOST);
            } else {
                widthMeasureSpec = MeasureSpec.makeMeasureSpec(wanted, MeasureSpec.EXACTLY);
            }
        }
        int maxHeight = Md3.dp(getContext(), PANEL_MAX_HEIGHT_DP);
        int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        if (heightMode != MeasureSpec.EXACTLY) {
            int wanted = heightMode == MeasureSpec.AT_MOST
                    ? Math.min(maxHeight, MeasureSpec.getSize(heightMeasureSpec))
                    : maxHeight;
            heightMeasureSpec = MeasureSpec.makeMeasureSpec(wanted, MeasureSpec.AT_MOST);
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        if (getMeasuredHeight() > maxHeight) {
            setMeasuredDimension(getMeasuredWidth(), maxHeight);
        }
    }

    public void setChatTitle(String title) {
        chatTitleView.setText(title == null ? TITLE_UNKNOWN : title);
    }

    public void setBusy(boolean busy) {
        this.busy = busy;
        generateView.setEnabled(!busy);
        generateView.setAlpha(busy ? 0.5f : 1.0f);
    }

    public void setStatus(String text) {
        if (text == null) {
            statusRow.setVisibility(View.GONE);
            statusView.setVisibility(View.GONE);
            progressView.setVisibility(View.GONE);
        } else {
            statusView.setText(text);
            statusView.setVisibility(View.VISIBLE);
            progressView.setVisibility(View.VISIBLE);
            statusRow.setVisibility(View.VISIBLE);
        }
    }

    public void setResult(String text) {
        resultView.setTextColor(Md3.onSurface(getContext()));
        resultView.setText(text == null ? "" : text);
    }

    public void setError(String text) {
        resultView.setTextColor(Md3.error(getContext()));
        resultView.setText(text == null ? "" : text);
    }

    public int getCount() {
        return count;
    }

    public String getLevel() {
        return level;
    }
}
