package com.dsh.tgxsummary;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * 保存在 Telegram X 自身进程里的配置。
 * 因为 hook 运行在 Telegram X 进程内，读写这份 SharedPreferences 是同进程操作，
 * 不依赖任何跨进程 IPC，所以最可靠。模块 App 里保存时会尝试用 root 把配置同步过来。
 */
public final class TgPrefs {

    public static final String FILE = "tgxsum";
    public static final String K_API = "api_key";
    public static final String K_BASE = "base_url";
    public static final String K_MODEL = "model";

    private TgPrefs() {
    }

    public static SharedPreferences sp(Context context) {
        return context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public static String apiKey(Context context) {
        try {
            String value = sp(context).getString(K_API, "");
            return value == null ? "" : value;
        } catch (Throwable t) {
            return "";
        }
    }

    public static String baseUrl(Context context) {
        try {
            String value = sp(context).getString(K_BASE, Config.DEF_BASE);
            if (value == null || value.trim().isEmpty()) {
                return Config.DEF_BASE;
            }
            return value.trim();
        } catch (Throwable t) {
            return Config.DEF_BASE;
        }
    }

    public static String model(Context context) {
        try {
            String value = sp(context).getString(K_MODEL, Config.DEF_MODEL);
            if (value == null || value.trim().isEmpty()) {
                return Config.DEF_MODEL;
            }
            return value.trim();
        } catch (Throwable t) {
            return Config.DEF_MODEL;
        }
    }

    public static boolean hasApiKey(Context context) {
        String key = apiKey(context);
        return key != null && !key.isEmpty();
    }
}
