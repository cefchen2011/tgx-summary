package com.dsh.tgxsummary;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;

/**
 * 跨进程配置桥：模块 App 进程保存设置，Telegram X 进程（hook 运行处）通过
 * ContentResolver.call 读取。之所以不用 XSharedPreferences，是因为它在部分
 * LSPosed/Android 版本上读不到模块的私有 SharedPreferences。
 */
public class ConfigProvider extends ContentProvider {

    public static final String AUTHORITY = "com.dsh.tgxsummary.config";
    public static final String URI_STRING = "content://" + AUTHORITY;
    public static final String METHOD_GET = "get_config";

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        Bundle result = new Bundle();
        if (!METHOD_GET.equals(method)) {
            return result;
        }
        try {
            Context context = getContext();
            if (context == null) {
                return result;
            }
            result.putString(Config.K_API, Config.apiKey(context));
            result.putString(Config.K_BASE, Config.baseUrl(context));
            result.putString(Config.K_MODEL, Config.model(context));
            result.putString(Config.K_LEVEL, Config.level(context));
            result.putInt(Config.K_COUNT, Config.count(context));
        } catch (Throwable ignored) {
            // 返回空 Bundle，调用方会退回默认值。
        }
        return result;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        return null;
    }

    @Override
    public String getType(Uri uri) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }
}
