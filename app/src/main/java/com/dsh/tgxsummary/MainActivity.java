package com.dsh.tgxsummary;

import android.app.Activity;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.text.TextUtils;
import android.util.Base64;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;

/**
 * Module settings screen, built entirely in code with Material 3 styling.
 * Third-party resources are never referenced: colors and shapes come from {@link Md3}.
 */
public class MainActivity extends Activity {

    private static final int FALLBACK_COUNT = 50;
    private static final int MIN_COUNT = 1;
    private static final int MAX_COUNT = 500;
    private static final int MAX_REPLY_CHARS = 200;
    private static final float FIELD_RADIUS_DP = 12f;
    private static final float BUTTON_RADIUS_DP = 22f;
    private static final float SEGMENT_RADIUS_DP = 20f;
    private static final int BUTTON_HEIGHT_DP = 44;
    private static final int SEGMENT_HEIGHT_DP = 40;

    private static final String LEVEL_SIMPLE = "l";
    private static final String LEVEL_STANDARD = "m";
    private static final String LEVEL_DETAILED = "h";

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private EditText apiKeyInput;
    private EditText baseUrlInput;
    private EditText modelInput;
    private EditText countInput;
    private TextView[] segmentViews;
    private final String[] segmentLevels = {LEVEL_SIMPLE, LEVEL_STANDARD, LEVEL_DETAILED};
    private TextView testButton;
    private TextView grantButton;
    private TextView statusView;
    private String level = LEVEL_STANDARD;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Md3.surface(this));

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int pad = Md3.dp(this, 20f);
        content.setPadding(pad, pad, pad, pad);
        scroll.addView(content, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView title = text("TGX 消息总结", 24f, Md3.onSurface(this), true);
        content.addView(title, matchWrap(0, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView subtitle = text("Telegram X · DeepSeek 消息总结模块", 13f, Md3.onSurfaceVariant(this), false);
        content.addView(subtitle, matchWrap(4, ViewGroup.LayoutParams.WRAP_CONTENT));

        content.addView(buildConfigCard(), matchWrap(16, ViewGroup.LayoutParams.WRAP_CONTENT));
        content.addView(buildDefaultsCard(), matchWrap(16, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView saveButton = button("保存", Md3.primary(this), Md3.onPrimary(this));
        saveButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveConfig();
                statusView.setText("已保存，正在同步到 Telegram X…");
                String base = baseUrlInput.getText().toString().trim();
                if (base.isEmpty()) {
                    base = Config.DEF_BASE;
                }
                String model = modelInput.getText().toString().trim();
                if (model.isEmpty()) {
                    model = Config.DEF_MODEL;
                }
                syncToTelegramX(apiKeyInput.getText().toString().trim(), base, model);
            }
        });
        content.addView(saveButton, matchWrap(20, Md3.dp(this, BUTTON_HEIGHT_DP)));

        testButton = button("测试连接", Md3.primaryContainer(this), Md3.onPrimaryContainer(this));
        testButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                testConnection();
            }
        });
        content.addView(testButton, matchWrap(10, Md3.dp(this, BUTTON_HEIGHT_DP)));

        statusView = text("未测试", 13f, Md3.onSurfaceVariant(this), false);
        statusView.setPadding(0, Md3.dp(this, 12f), 0, 0);
        content.addView(statusView, matchWrap(0, ViewGroup.LayoutParams.WRAP_CONTENT));

        content.addView(buildHelpCard(), matchWrap(16, ViewGroup.LayoutParams.WRAP_CONTENT));

        grantButton = button("为 Telegram X 授予悬浮窗权限（需 root）",
                Md3.surfaceContainer(this), Md3.onSurfaceVariant(this));
        grantButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                grantOverlayPermission();
            }
        });
        content.addView(grantButton, matchWrap(10, Md3.dp(this, BUTTON_HEIGHT_DP)));

        setContentView(scroll);
        applyConfig();
    }

    @Override
    protected void onDestroy() {
        mainHandler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    private View buildConfigCard() {
        LinearLayout card = cardView();

        card.addView(label("DeepSeek API Key"), matchWrap(0, ViewGroup.LayoutParams.WRAP_CONTENT));
        apiKeyInput = inputField("sk-...", InputType.TYPE_CLASS_TEXT);
        card.addView(apiKeyInput, matchWrap(6, ViewGroup.LayoutParams.WRAP_CONTENT));

        card.addView(label("接口地址"), matchWrap(12, ViewGroup.LayoutParams.WRAP_CONTENT));
        baseUrlInput = inputField(Config.DEF_BASE,
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        card.addView(baseUrlInput, matchWrap(6, ViewGroup.LayoutParams.WRAP_CONTENT));

        card.addView(label("模型"), matchWrap(12, ViewGroup.LayoutParams.WRAP_CONTENT));
        modelInput = inputField(Config.DEF_MODEL, InputType.TYPE_CLASS_TEXT);
        card.addView(modelInput, matchWrap(6, ViewGroup.LayoutParams.WRAP_CONTENT));

        return card;
    }

    private View buildDefaultsCard() {
        LinearLayout card = cardView();

        card.addView(label("默认条数"), matchWrap(0, ViewGroup.LayoutParams.WRAP_CONTENT));
        countInput = inputField("50", InputType.TYPE_CLASS_NUMBER);
        card.addView(countInput, matchWrap(6, ViewGroup.LayoutParams.WRAP_CONTENT));

        card.addView(label("详细程度"), matchWrap(12, ViewGroup.LayoutParams.WRAP_CONTENT));
        card.addView(buildSegmentControl(), matchWrap(6, Md3.dp(this, SEGMENT_HEIGHT_DP)));

        return card;
    }

    private View buildSegmentControl() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setBackground(Md3.rounded(this, Md3.surfaceContainer(this), SEGMENT_RADIUS_DP));

        segmentViews = new TextView[3];
        String[] labels = {"简洁", "标准", "详细"};
        for (int i = 0; i < labels.length; i++) {
            final String value = segmentLevels[i];
            TextView segment = text(labels[i], 13f, Md3.onSurfaceVariant(this), false);
            segment.setGravity(Gravity.CENTER);
            segment.setClickable(true);
            segment.setFocusable(true);
            segment.setContentDescription(labels[i]);
            segment.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    selectLevel(value);
                }
            });
            segmentViews[i] = segment;
            row.addView(segment, new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        }
        return row;
    }

    private View buildHelpCard() {
        LinearLayout card = cardView();
        StringBuilder help = new StringBuilder();
        help.append("① 在 LSPosed 中启用本模块，并勾选作用域 org.thunderdog.challegram；");
        help.append("\n");
        help.append("② 为 Telegram X 授予悬浮窗权限（使用下方按钮，需要 root）；");
        help.append("\n");
        help.append("③ 在 Telegram X 中打开任意聊天，点击屏幕边缘的半圆，展开后设置条数与详细程度，点「生成总结」。");
        help.append("\n");
        help.append("④ 保存后会自动把配置同步到 Telegram X（需要 root）。修改配置后请重启一次 Telegram X 使其生效。");

        TextView body = text(help.toString(), 13f, Md3.onSurfaceVariant(this), false);
        body.setLineSpacing(0f, 1.35f);
        card.addView(body, matchWrap(0, ViewGroup.LayoutParams.WRAP_CONTENT));
        return card;
    }

    private LinearLayout cardView() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        int pad = Md3.dp(this, 16f);
        card.setPadding(pad, pad, pad, pad);
        Md3.card(card, this, Md3.surfaceContainer(this), 20f);
        return card;
    }

    private TextView label(String value) {
        return text(value, 13f, Md3.onSurfaceVariant(this), false);
    }

    private EditText inputField(String hint, int inputType) {
        EditText field = new EditText(this);
        field.setHint(hint);
        field.setHintTextColor(Md3.withAlpha(Md3.onSurfaceVariant(this), 150));
        field.setTextSize(15f);
        field.setTextColor(Md3.onSurface(this));
        field.setInputType(inputType);
        field.setSingleLine(true);
        field.setBackground(Md3.rounded(this, Md3.surfaceContainer(this), FIELD_RADIUS_DP));
        int pad = Md3.dp(this, 12f);
        field.setPadding(pad, pad, pad, pad);
        return field;
    }

    private TextView text(String value, float sizeSp, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sizeSp);
        view.setTextColor(color);
        if (bold) {
            view.setTypeface(Typeface.DEFAULT_BOLD);
        }
        return view;
    }

    private TextView button(String value, int background, int textColor) {
        TextView view = text(value, 15f, textColor, true);
        view.setGravity(Gravity.CENTER);
        view.setBackground(Md3.rounded(this, background, BUTTON_RADIUS_DP));
        view.setClickable(true);
        view.setFocusable(true);
        view.setContentDescription(value);
        return view;
    }

    private LinearLayout.LayoutParams matchWrap(int topMarginDp, int height) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, height);
        params.topMargin = Md3.dp(this, topMarginDp);
        return params;
    }

    private void applyConfig() {
        apiKeyInput.setText(safe(Config.apiKey(this)));
        baseUrlInput.setText(safe(Config.baseUrl(this)));
        modelInput.setText(safe(Config.model(this)));
        countInput.setText(String.valueOf(clampCount(Config.count(this))));
        selectLevel(normalizeLevel(Config.level(this)));
    }

    private void saveConfig() {
        Config.save(this,
                apiKeyInput.getText().toString().trim(),
                baseUrlInput.getText().toString().trim(),
                modelInput.getText().toString().trim(),
                parseCount(countInput.getText().toString()),
                level);
    }

    /**
     * 通过 root 把配置写进 Telegram X 自己的 SharedPreferences 文件
     * (shared_prefs/tgxsum.xml)。hook 与 Telegram X 同进程，读取该文件 100% 可靠。
     */
    private void syncToTelegramX(final String apiKey, final String baseUrl, final String model) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                String status;
                try {
                    String key = apiKey == null ? "" : apiKey.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
                    String base = baseUrl == null ? "" : baseUrl.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
                    String name = model == null ? "" : model.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");

                    String xml = "<?xml version='1.0' encoding='utf-8' standalone='yes' ?>\n"
                            + "<map>\n"
                            + "    <string name=\"api_key\">" + key + "</string>\n"
                            + "    <string name=\"base_url\">" + base + "</string>\n"
                            + "    <string name=\"model\">" + name + "</string>\n"
                            + "</map>\n";
                    String b64 = Base64.encodeToString(xml.getBytes("UTF-8"), Base64.NO_WRAP);
                    String script = "echo " + b64
                            + " | base64 -d > /data/data/org.thunderdog.challegram/shared_prefs/tgxsum.xml"
                            + "; U=$(stat -c %u /data/data/org.thunderdog.challegram)"
                            + "; chown $U:$U /data/data/org.thunderdog.challegram/shared_prefs/tgxsum.xml"
                            + "; chmod 660 /data/data/org.thunderdog.challegram/shared_prefs/tgxsum.xml"
                            + "; restorecon /data/data/org.thunderdog.challegram/shared_prefs/tgxsum.xml"
                            + "; echo SYNC_OK";

                    Process process = Runtime.getRuntime().exec(new String[]{"su", "-c", script});
                    String output = readStream(process.getInputStream());
                    String error = readStream(process.getErrorStream());
                    int exitCode = process.waitFor();

                    if (output.contains("SYNC_OK")) {
                        status = "已保存，并已同步到 Telegram X";
                    } else {
                        String reason = error.trim();
                        if (reason.isEmpty()) {
                            reason = output.trim();
                        }
                        reason = reason.replace("\n", " ").replace("\r", " ").trim();
                        if (reason.isEmpty()) {
                            reason = "退出码 " + exitCode;
                        }
                        if (reason.length() > 60) {
                            reason = reason.substring(0, 60);
                        }
                        status = "已保存（同步 Telegram X 失败：" + reason
                                + "）。若总结提示未配置 API Key，请在 Telegram X 里重启应用后重试";
                    }
                } catch (final Throwable failure) {
                    final String detail = failure.getMessage();
                    status = "已保存（同步 Telegram X 失败："
                            + (detail == null ? failure.toString() : detail)
                            + "）。若总结提示未配置 API Key，请在 Telegram X 里重启应用后重试";
                }
                final String message = status;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (statusView != null) {
                            statusView.setText(message);
                        }
                    }
                });
            }
        }, "tgx-sync-config").start();
    }

    private void testConnection() {
        saveConfig();
        final DeepSeekClient client = new DeepSeekClient(
                baseUrlInput.getText().toString().trim(),
                apiKeyInput.getText().toString().trim(),
                modelInput.getText().toString().trim());

        testButton.setEnabled(false);
        testButton.setAlpha(0.5f);
        statusView.setText("正在测试…");

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final String reply = client.test();
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            finishTest("连接成功：" + clip(reply));
                        }
                    });
                } catch (final Throwable failure) {
                    final String message = failure.getMessage();
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            finishTest("失败：" + (message == null ? failure.toString() : message));
                        }
                    });
                }
            }
        }, "tgx-test-connection").start();
    }

    private void finishTest(String status) {
        testButton.setEnabled(true);
        testButton.setAlpha(1.0f);
        statusView.setText(status);
    }

    private void grantOverlayPermission() {
        grantButton.setEnabled(false);
        statusView.setText("正在申请悬浮窗权限…");
        new Thread(new Runnable() {
            @Override
            public void run() {
                String message;
                try {
                    Process process = Runtime.getRuntime().exec(new String[]{
                            "su", "-c",
                            "appops set org.thunderdog.challegram SYSTEM_ALERT_WINDOW allow"});
                    String output = readStream(process.getInputStream());
                    String error = readStream(process.getErrorStream());
                    process.waitFor();
                    StringBuilder builder = new StringBuilder("授权命令已执行");
                    if (output.length() > 0) {
                        builder.append("\n").append(output.trim());
                    }
                    if (error.length() > 0) {
                        builder.append("\n").append(error.trim());
                    }
                    message = builder.toString();
                } catch (final Throwable failure) {
                    final String detail = failure.getMessage();
                    message = "失败：" + (detail == null ? failure.toString() : detail);
                }
                final String result = message;
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        grantButton.setEnabled(true);
                        statusView.setText(result);
                    }
                });
            }
        }, "tgx-grant-overlay").start();
    }

    private static String readStream(InputStream input) {
        if (input == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new InputStreamReader(input, "UTF-8"));
            String line;
            while ((line = reader.readLine()) != null) {
                if (builder.length() > 0) {
                    builder.append("\n");
                }
                builder.append(line);
            }
        } catch (Exception ignored) {
            // Best effort: a broken pipe while reading root output is not fatal.
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (Exception ignored) {
                    // Ignored on purpose.
                }
            }
        }
        return builder.toString();
    }

    private void selectLevel(String value) {
        level = normalizeLevel(value);
        if (segmentViews == null) {
            return;
        }
        for (int i = 0; i < segmentViews.length; i++) {
            TextView segment = segmentViews[i];
            if (segment == null) {
                continue;
            }
            if (segmentLevels[i].equals(level)) {
                segment.setBackground(Md3.rounded(this, Md3.primaryContainer(this), SEGMENT_RADIUS_DP));
                segment.setTextColor(Md3.onPrimaryContainer(this));
                segment.setTypeface(Typeface.DEFAULT_BOLD);
            } else {
                segment.setBackground(null);
                segment.setTextColor(Md3.onSurfaceVariant(this));
                segment.setTypeface(Typeface.DEFAULT);
            }
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static String normalizeLevel(String value) {
        if (LEVEL_SIMPLE.equals(value) || LEVEL_DETAILED.equals(value)) {
            return value;
        }
        return LEVEL_STANDARD;
    }

    private static int parseCount(String raw) {
        String text = raw == null ? "" : raw.trim();
        int value = FALLBACK_COUNT;
        if (!TextUtils.isEmpty(text)) {
            try {
                value = Integer.parseInt(text);
            } catch (NumberFormatException failure) {
                value = FALLBACK_COUNT;
            }
        }
        return clampCount(value);
    }

    private static int clampCount(int value) {
        if (value < MIN_COUNT) {
            return MIN_COUNT;
        }
        if (value > MAX_COUNT) {
            return MAX_COUNT;
        }
        return value;
    }

    private static String clip(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.length() <= MAX_REPLY_CHARS) {
            return trimmed;
        }
        return trimmed.substring(0, MAX_REPLY_CHARS) + "…";
    }
}
