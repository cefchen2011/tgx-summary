package com.dsh.tgxsummary;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import de.robv.android.xposed.XSharedPreferences;

/**
 * 在 Telegram X 进程内读取模块设置。
 * 依次尝试：XSharedPreferences -> 模块 App 的 ConfigProvider（ContentResolver.call）。
 * 全部失败时退回内置默认值，保证不会因为读不到配置而崩溃。
 */
public final class HookConfig {

    public final String apiKey;
    public final String baseUrl;
    public final String model;
    public final String level;
    public final int count;

    private HookConfig(String apiKey, String baseUrl, String model, int count, String level) {
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.model = model;
        this.count = count;
        this.level = level;
    }

    public static HookConfig load() {
        return load(null);
    }

    public static HookConfig load(Context ctx) {
        String api = "";
        String base = Config.DEF_BASE;
        String model = Config.DEF_MODEL;
        String level = Config.DEF_LEVEL;
        int count = Config.DEF_COUNT;

        // 1) XSharedPreferences（LSPosed 支持时可用）
        try {
            XSharedPreferences prefs = new XSharedPreferences("com.dsh.tgxsummary", Config.FILE);
            prefs.reload();
            api = safe(prefs.getString(Config.K_API, ""), api);
            base = safe(prefs.getString(Config.K_BASE, base), base);
            model = safe(prefs.getString(Config.K_MODEL, model), model);
            level = safe(prefs.getString(Config.K_LEVEL, level), level);
            int c = prefs.getInt(Config.K_COUNT, count);
            if (c > 0) {
                count = c;
            }
        } catch (Throwable t) {
            Td.log("HookConfig: XSharedPreferences 不可用: " + t);
        }

        // 2) 显式组件有序广播（不受包可见性限制）
        if (api == null || api.isEmpty()) {
            try {
                Bundle bundle = fetchViaBroadcast(ctx);
                if (bundle != null) {
                    String v = bundle.getString(Config.K_API);
                    if (v != null && !v.isEmpty()) {
                        api = v;
                    }
                    base = safe(bundle.getString(Config.K_BASE), base);
                    model = safe(bundle.getString(Config.K_MODEL), model);
                    level = safe(bundle.getString(Config.K_LEVEL), level);
                    int c = bundle.getInt(Config.K_COUNT, count);
                    if (c > 0) {
                        count = c;
                    }
                    Td.log("HookConfig: 通过广播读取成功 apiKeyLen=" + api.length());
                }
            } catch (Throwable t) {
                Td.log("HookConfig: 广播读取失败: " + t);
            }
        }

        // 3) ContentProvider 兜底（可能被包可见性拦截）
        if (api == null || api.isEmpty()) {
            try {
                if (ctx != null) {
                    Bundle bundle = ctx.getContentResolver().call(
                            Uri.parse(ConfigProvider.URI_STRING), ConfigProvider.METHOD_GET, null, null);
                    if (bundle != null) {
                        String v = bundle.getString(Config.K_API);
                        if (v != null && !v.isEmpty()) {
                            api = v;
                        }
                        base = safe(bundle.getString(Config.K_BASE), base);
                        model = safe(bundle.getString(Config.K_MODEL), model);
                        level = safe(bundle.getString(Config.K_LEVEL), level);
                        int c = bundle.getInt(Config.K_COUNT, count);
                        if (c > 0) {
                            count = c;
                        }
                        Td.log("HookConfig: 通过 ConfigProvider 读取成功 apiKeyLen="
                                + (api == null ? 0 : api.length()));
                    }
                }
            } catch (Throwable t) {
                Td.log("HookConfig: ConfigProvider 读取失败: " + t);
            }
        }

        if (api == null) {
            api = "";
        }
        if (base == null || base.trim().isEmpty()) {
            base = Config.DEF_BASE;
        }
        if (model == null || model.trim().isEmpty()) {
            model = Config.DEF_MODEL;
        }
        if (level == null || level.trim().isEmpty()) {
            level = Config.DEF_LEVEL;
        }
        if (count <= 0) {
            count = Config.DEF_COUNT;
        }
        Td.log("HookConfig: 最终 apiKeyLen=" + api.length() + " base=" + base + " model=" + model
                + " level=" + level + " count=" + count);
        return new HookConfig(api, base, model, count, level);
    }

    /** 通过显式组件的有序广播向模块 App 进程请求配置。 */
    private static Bundle fetchViaBroadcast(Context ctx) throws Exception {
        if (ctx == null) {
            return null;
        }
        final Bundle[] holder = new Bundle[1];
        final CountDownLatch latch = new CountDownLatch(1);
        BroadcastReceiver reply = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                try {
                    holder[0] = getResultExtras(true);
                } catch (Throwable ignored) {
                    // 保持 null，调用方会用默认值。
                }
                latch.countDown();
            }
        };
        Intent intent = new Intent(ConfigReceiver.ACTION);
        intent.setComponent(new ComponentName("com.dsh.tgxsummary", ConfigReceiver.COMPONENT));
        // 关键：模块 App 未启动过 / 被停止时，默认不会收到广播，必须显式包含 STOPPED 包。
        intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
        ctx.sendOrderedBroadcast(intent, null, reply, null, Activity.RESULT_OK, null, null);
        if (!latch.await(4000L, TimeUnit.MILLISECONDS)) {
            Td.log("HookConfig: 广播读取超时");
        }
        return holder[0];
    }

    private static String safe(String value, String fallback) {
        if (value == null || value.trim().isEmpty()) {
            return fallback;
        }
        return value;
    }
}
