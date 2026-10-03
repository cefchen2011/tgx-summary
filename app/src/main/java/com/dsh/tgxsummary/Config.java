package com.dsh.tgxsummary;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Settings storage used by the module's own UI process (the settings app).
 * The hook process reads the very same preference file through
 * {@link HookConfig} (XSharedPreferences).
 */
final class Config {

    public static final String FILE = "config";

    public static final String K_API = "api_key";
    public static final String K_BASE = "base_url";
    public static final String K_MODEL = "model";
    public static final String K_COUNT = "count";
    public static final String K_LEVEL = "level";

    public static final String DEF_BASE = "https://api.deepseek.com";
    public static final String DEF_MODEL = "deepseek-chat";
    public static final String DEF_LEVEL = "m";
    public static final int DEF_COUNT = 50;

    private Config() {
    }

    public static SharedPreferences sp(Context c) {
        if (c == null) {
            return null;
        }
        try {
            return c.getSharedPreferences(FILE, Context.MODE_PRIVATE);
        } catch (Throwable t) {
            return null;
        }
    }

    public static String apiKey(Context c) {
        SharedPreferences prefs = sp(c);
        if (prefs == null) {
            return "";
        }
        try {
            String value = prefs.getString(K_API, "");
            return value == null ? "" : value;
        } catch (Throwable t) {
            return "";
        }
    }

    public static String baseUrl(Context c) {
        SharedPreferences prefs = sp(c);
        if (prefs == null) {
            return DEF_BASE;
        }
        try {
            String value = prefs.getString(K_BASE, DEF_BASE);
            return (value == null || value.trim().isEmpty()) ? DEF_BASE : value;
        } catch (Throwable t) {
            return DEF_BASE;
        }
    }

    public static String model(Context c) {
        SharedPreferences prefs = sp(c);
        if (prefs == null) {
            return DEF_MODEL;
        }
        try {
            String value = prefs.getString(K_MODEL, DEF_MODEL);
            return (value == null || value.trim().isEmpty()) ? DEF_MODEL : value;
        } catch (Throwable t) {
            return DEF_MODEL;
        }
    }

    public static int count(Context c) {
        SharedPreferences prefs = sp(c);
        if (prefs == null) {
            return DEF_COUNT;
        }
        try {
            int value = prefs.getInt(K_COUNT, DEF_COUNT);
            return value <= 0 ? DEF_COUNT : value;
        } catch (Throwable t) {
            return DEF_COUNT;
        }
    }

    public static String level(Context c) {
        SharedPreferences prefs = sp(c);
        if (prefs == null) {
            return DEF_LEVEL;
        }
        try {
            String value = prefs.getString(K_LEVEL, DEF_LEVEL);
            return (value == null || value.trim().isEmpty()) ? DEF_LEVEL : value;
        } catch (Throwable t) {
            return DEF_LEVEL;
        }
    }

    public static void save(Context c, String api, String base, String model, int count, String level) {
        SharedPreferences prefs = sp(c);
        if (prefs == null) {
            return;
        }
        String apiValue = api == null ? "" : api.trim();
        String baseValue = base == null ? "" : base.trim();
        String modelValue = model == null ? "" : model.trim();
        String levelValue = level == null ? "" : level.trim();
        if (baseValue.isEmpty()) {
            baseValue = DEF_BASE;
        }
        if (modelValue.isEmpty()) {
            modelValue = DEF_MODEL;
        }
        if (levelValue.isEmpty()) {
            levelValue = DEF_LEVEL;
        }
        int countValue = count <= 0 ? DEF_COUNT : count;

        try {
            SharedPreferences.Editor editor = prefs.edit();
            editor.putString(K_API, apiValue);
            editor.putString(K_BASE, baseValue);
            editor.putString(K_MODEL, modelValue);
            editor.putInt(K_COUNT, countValue);
            editor.putString(K_LEVEL, levelValue);
            editor.apply();
        } catch (Throwable t) {
            // Nothing useful to do when the preference file cannot be written.
        }
    }
}
