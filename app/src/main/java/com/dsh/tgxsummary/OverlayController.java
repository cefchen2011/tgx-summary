package com.dsh.tgxsummary;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;

/**
 * Owns the floating handle and the summary panel.
 *
 * <p>Everything runs on the main thread. Views are added and removed together with the
 * foreground activity count so the overlays never outlive Telegram X.</p>
 */
final class OverlayController implements Application.ActivityLifecycleCallbacks {

    private static final int HANDLE_WIDTH_DP = 30;
    private static final int HANDLE_HEIGHT_DP = 64;
    private static final int PANEL_WIDTH_DP = 300;
    private static final int PANEL_MARGIN_DP = 8;
    private static final int PANEL_TOP_DP = 72;
    private static final int EDGE_MARGIN_DP = 4;
    private static final long HIDE_DELAY_MS = 2500L;

    private static OverlayController instance;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Context app;
    private final WindowManager wm;

    private OverlayPanel panel;
    private SemiCircleView handle;
    private WindowManager.LayoutParams panelLp;
    private WindowManager.LayoutParams handleLp;

    private boolean lifecycleRegistered = false;
    private boolean handleAdded = false;
    private boolean panelAdded = false;
    private boolean expanded = false;
    private boolean dragging = false;
    private int activityCount = 0;
    private Runnable pendingHide;
    private int handleY = Integer.MIN_VALUE;
    private boolean handleVisible = false;
    private boolean panelVisible = false;

    private float downRawY = 0f;
    private int downHandleY = 0;
    private int screenWidth = 0;
    private int screenHeight = 0;

    private OverlayController(Context context) {
        app = context.getApplicationContext();
        wm = (WindowManager) app.getSystemService(Context.WINDOW_SERVICE);
    }

    /** Idempotent entry point; safe to call on every hook invocation. */
    public static void start(final Context context) {
        if (context == null) {
            return;
        }
        final Context appContext = context.getApplicationContext() == null
                ? context
                : context.getApplicationContext();
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                try {
                    get(appContext).install();
                } catch (Throwable failure) {
                    Td.log("OverlayController start failed: " + failure);
                }
            }
        });
    }

    private static synchronized OverlayController get(Context appContext) {
        if (instance == null) {
            instance = new OverlayController(appContext);
        }
        return instance;
    }

    private void install() {
        if (lifecycleRegistered) {
            return;
        }
        if (app instanceof Application) {
            try {
                ((Application) app).registerActivityLifecycleCallbacks(this);
                lifecycleRegistered = true;
            } catch (Throwable failure) {
                Td.log("OverlayController registerActivityLifecycleCallbacks failed: " + failure);
            }
        }
    }

    private void refreshMetrics() {
        try {
            screenWidth = app.getResources().getDisplayMetrics().widthPixels;
            screenHeight = app.getResources().getDisplayMetrics().heightPixels;
        } catch (Throwable failure) {
            Td.log("OverlayController refreshMetrics failed: " + failure);
        }
    }

    private int windowType() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        }
        return WindowManager.LayoutParams.TYPE_PHONE;
    }

    private void ensurePanel() {
        if (panel != null) {
            return;
        }
        OverlayPanel created = new OverlayPanel(app, new PanelCallback());
        created.setFocusableInTouchMode(true);
        panel = created;
        created.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if (keyCode == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_UP) {
                    collapse();
                    return true;
                }
                return false;
            }
        });
    }

    private void ensureHandle() {
        if (handle != null) {
            return;
        }
        handle = new SemiCircleView(app);
        handle.setOnTapListener(new Runnable() {
            @Override
            public void run() {
                expand();
            }
        });
        handle.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                return onHandleTouch(event);
            }
        });
    }

    private boolean onHandleTouch(MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            downRawY = event.getRawY();
            downHandleY = handleLp == null ? 0 : handleLp.y;
            dragging = false;
            return false;
        }
        if (action == MotionEvent.ACTION_MOVE) {
            if (handleLp == null) {
                return false;
            }
            int slop = ViewConfiguration.get(app).getScaledTouchSlop();
            if (slop <= 0) {
                slop = Md3.dp(app, 8f);
            }
            float delta = event.getRawY() - downRawY;
            if (!dragging && Math.abs(delta) > slop) {
                dragging = true;
            }
            if (dragging) {
                handleLp.y = clampHandleY(Math.round(downHandleY + delta));
                handleY = handleLp.y;
                updateView(handle, handleLp, "handle-move");
                return true;
            }
            return false;
        }
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            boolean wasDragging = dragging;
            dragging = false;
            return wasDragging;
        }
        return false;
    }

    private void showHandle() {
        refreshMetrics();
        ensureHandle();
        if (wm == null) {
            Td.log("OverlayController: WindowManager unavailable");
            return;
        }
        if (!handleAdded) {
            handle.setEdge(Gravity.RIGHT);
            int width = Md3.dp(app, HANDLE_WIDTH_DP);
            int height = Md3.dp(app, HANDLE_HEIGHT_DP);
            handleLp = new WindowManager.LayoutParams(
                    width, height, windowType(),
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT);
            handleLp.gravity = Gravity.TOP | Gravity.START;
            handleLp.x = screenWidth - width;
            if (handleY == Integer.MIN_VALUE) {
                handleY = screenHeight / 2 - height / 2;
            }
            handleLp.y = clampHandleY(handleY);

            try {
                wm.addView(handle, handleLp);
                handleAdded = true;
                Td.log("OverlayController addView(handle) ok x=" + handleLp.x + " y=" + handleLp.y
                        + " w=" + width + " h=" + height);
            } catch (Throwable failure) {
                Td.log("OverlayController addView(handle) failed, overlay permission missing? " + failure);
            }
        }
        setHandleVisible(true);
    }

    /** 只切可见性、不 remove/add 窗口：避免闪烁，也避免拖动位置被重置。 */
    private void setHandleVisible(boolean visible) {
        if (handle == null || handleLp == null || !handleAdded) {
            return;
        }
        if (handleVisible == visible) {
            return;
        }
        handleVisible = visible;
        int base = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
        handleLp.flags = visible ? base : (base | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
        handle.setVisibility(visible ? View.VISIBLE : View.GONE);
        updateView(handle, handleLp, "handle-visibility");
    }

    private void showPanel() {
        refreshMetrics();
        ensurePanel();
        if (wm == null) {
            Td.log("OverlayController: WindowManager unavailable");
            return;
        }
        if (!panelAdded) {
            addPanelWindow();
        }
        setPanelVisible(true);
    }

    private void addPanelWindow() {
        int width = Md3.dp(app, PANEL_WIDTH_DP);
        int panelWidth = Math.min(width, Math.max(0, screenWidth - Md3.dp(app, PANEL_MARGIN_DP * 2)));
        panelLp = new WindowManager.LayoutParams(
                panelWidth, WindowManager.LayoutParams.WRAP_CONTENT, windowType(),
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        panelLp.gravity = Gravity.TOP | Gravity.START;
        panelLp.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE;
        int x = screenWidth - panelWidth - Md3.dp(app, PANEL_MARGIN_DP);
        if (x < 0) {
            x = 0;
        }
        panelLp.x = x;
        panelLp.y = clampY(Md3.dp(app, PANEL_TOP_DP));

        try {
            wm.addView(panel, panelLp);
            panelAdded = true;
            Td.log("OverlayController addView(panel) ok x=" + panelLp.x + " y=" + panelLp.y
                    + " w=" + panelWidth);
        } catch (Throwable failure) {
            Td.log("OverlayController addView(panel) failed, overlay permission missing? " + failure);
        }
    }

    /** 与半圆同理：面板也只切可见性。 */
    private void setPanelVisible(boolean visible) {
        if (panel == null || panelLp == null || !panelAdded) {
            return;
        }
        if (panelVisible == visible) {
            return;
        }
        panelVisible = visible;
        panel.setVisibility(visible ? View.VISIBLE : View.GONE);
        updateView(panel, panelLp, "panel-visibility");
    }

    private void expand() {
        refreshMetrics();
        removeHandle();
        ensurePanel();
        panel.setChatTitle(Td.currentChatTitle);
        showPanel();
        expanded = true;
        try {
            panel.requestFocus();
        } catch (Throwable failure) {
            Td.log("OverlayController requestFocus failed: " + failure);
        }
    }

    private void collapse() {
        removePanel();
        expanded = false;
        showHandle();
    }

    private void generate(final int count, final String level) {
        Td.log("generate 被调用 count=" + count + " level=" + level
                + " expanded=" + expanded + " panelAdded=" + panelAdded);
        ensurePanel();
        if (!expanded || !panelAdded) {
            Td.log("OverlayController generate ignored: panel is not showing");
            return;
        }
        if (!Td.ready()) {
            panel.setError("TDLib 未就绪，请重启 Telegram X 后重试");
            return;
        }
        final long chatId = Td.currentChatId;
        panel.setChatTitle(Td.currentChatTitle);
        if (chatId == 0L) {
            panel.setError("未识别到当前会话：请先在 Telegram X 中打开一个聊天，再点边缘半圆生成总结");
            return;
        }
        panel.setBusy(true);
        panel.setStatus("准备中…");
        try {
            ChatSummarizer.run(app, chatId, count, level, new ChatSummarizer.Callback() {
                @Override
                public void onProgress(final String status) {
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            panel.setStatus(status);
                        }
                    });
                }

                @Override
                public void onDone(final String result) {
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            panel.setBusy(false);
                            panel.setResult(result);
                            panel.setStatus(null);
                        }
                    });
                }

                @Override
                public void onError(final String message) {
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            panel.setBusy(false);
                            panel.setError(message);
                            panel.setStatus(null);
                        }
                    });
                }
            });
        } catch (Throwable failure) {
            Td.log("OverlayController ChatSummarizer.run failed: " + failure);
            panel.setBusy(false);
            panel.setError(String.valueOf(failure));
        }
    }

    private void removeHandle() {
        setHandleVisible(false);
    }

    private void removePanel() {
        setPanelVisible(false);
    }

    private void removeAll() {
        removeHandle();
        removePanel();
        expanded = false;
    }

    /** Keeps the draggable handle fully on screen. */
    private int clampHandleY(int y) {
        int handleHeight = Md3.dp(app, HANDLE_HEIGHT_DP);
        int topLimit = 0;
        int bottomLimit = screenHeight - handleHeight;
        if (bottomLimit < topLimit) {
            return topLimit;
        }
        if (y < topLimit) {
            return topLimit;
        }
        if (y > bottomLimit) {
            return bottomLimit;
        }
        return y;
    }

    private int clampY(int y) {
        int topLimit = Md3.dp(app, EDGE_MARGIN_DP);
        int bottomLimit = screenHeight - Md3.dp(app, EDGE_MARGIN_DP);
        if (bottomLimit < topLimit) {
            return topLimit;
        }
        if (y < topLimit) {
            return topLimit;
        }
        if (y > bottomLimit) {
            return bottomLimit;
        }
        return y;
    }

    private void updateView(View view, WindowManager.LayoutParams params, String tag) {
        if (view == null || params == null || wm == null) {
            return;
        }
        try {
            wm.updateViewLayout(view, params);
        } catch (Throwable failure) {
            Td.log("OverlayController updateViewLayout(" + tag + ") failed: " + failure);
        }
    }

    private final class PanelCallback implements OverlayPanel.Callback {
        @Override
        public void onGenerate(int count, String level) {
            generate(count, level);
        }

        @Override
        public void onCollapse() {
            collapse();
        }
    }

    @Override
    public void onActivityResumed(Activity activity) {
        activityCount++;
        Td.log("lifecycle resume -> count=" + activityCount + " act=" + activity.getClass().getName());
        if (activityCount == 1) {
            cancelPendingHide();
            showHandle();
        }
    }

    @Override
    public void onActivityPaused(Activity activity) {
        activityCount--;
        Td.log("lifecycle pause -> count=" + activityCount + " act=" + activity.getClass().getName());
        if (activityCount <= 0) {
            activityCount = 0;
            scheduleHide();
        }
    }

    /** 防抖：短暂的 pause/resume 抖动不再让悬浮窗闪烁。 */
    private void scheduleHide() {
        if (pendingHide != null) {
            return;
        }
        pendingHide = new Runnable() {
            @Override
            public void run() {
                pendingHide = null;
                if (activityCount <= 0) {
                    removeAll();
                }
            }
        };
        mainHandler.postDelayed(pendingHide, HIDE_DELAY_MS);
    }

    private void cancelPendingHide() {
        if (pendingHide != null) {
            mainHandler.removeCallbacks(pendingHide);
            pendingHide = null;
        }
    }

    @Override
    public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
    }

    @Override
    public void onActivityStarted(Activity activity) {
    }

    @Override
    public void onActivityStopped(Activity activity) {
    }

    @Override
    public void onActivitySaveInstanceState(Activity activity, Bundle outState) {
    }

    @Override
    public void onActivityDestroyed(Activity activity) {
    }
}
