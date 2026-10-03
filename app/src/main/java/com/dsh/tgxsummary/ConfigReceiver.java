package com.dsh.tgxsummary;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

/**
 * 跨进程配置桥（广播版）。
 * Telegram X 进程通过「显式组件的有序广播」请求配置，本接收器把设置写进 result extras。
 * 选择广播而不是 ContentProvider，是因为 Android 11+ 的包可见性会拦截跨包 authority，
 * 而显式组件 Intent 不受该限制。
 */
public class ConfigReceiver extends BroadcastReceiver {

    public static final String ACTION = "com.dsh.tgxsummary.action.GET_CONFIG";
    public static final String COMPONENT = "com.dsh.tgxsummary.ConfigReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        Bundle result = new Bundle();
        try {
            result.putString(Config.K_API, Config.apiKey(context));
            result.putString(Config.K_BASE, Config.baseUrl(context));
            result.putString(Config.K_MODEL, Config.model(context));
            result.putString(Config.K_LEVEL, Config.level(context));
            result.putInt(Config.K_COUNT, Config.count(context));
        } catch (Throwable ignored) {
            // 返回空 Bundle，调用方会退回默认值。
        }
        setResultExtras(result);
    }
}
