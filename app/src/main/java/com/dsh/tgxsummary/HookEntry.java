package com.dsh.tgxsummary;

import android.app.Application;
import android.app.Instrumentation;
import android.content.Context;

import java.util.concurrent.atomic.AtomicBoolean;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * LSPosed entry point. It only reacts to Telegram X and boots the module once
 * the application context is available.
 */
public class HookEntry implements IXposedHookLoadPackage {

    private static final String TARGET_PACKAGE = "org.thunderdog.challegram";

    private static final AtomicBoolean HOOKS_INSTALLED = new AtomicBoolean(false);
    private static final AtomicBoolean BOOTED = new AtomicBoolean(false);

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (lpparam == null || !TARGET_PACKAGE.equals(lpparam.packageName)) {
            return;
        }
        if (!HOOKS_INSTALLED.compareAndSet(false, true)) {
            return;
        }

        final ClassLoader loader = lpparam.classLoader;

        try {
            XposedHelpers.findAndHookMethod(Application.class, "attach", Context.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    try {
                        Context ctx = param.args != null && param.args.length > 0 ? (Context) param.args[0] : null;
                        boot(ctx, loader);
                    } catch (Throwable t) {
                        Td.log("Application.attach hook 回调失败: " + t);
                    }
                }
            });
            Td.log("已 hook Application.attach");
        } catch (Throwable t) {
            XposedBridge.log("[TGXSum] hook Application.attach 失败: " + t);
        }

        try {
            XposedHelpers.findAndHookMethod(Instrumentation.class, "callApplicationOnCreate", Application.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            try {
                                Context ctx = param.args != null && param.args.length > 0
                                        ? (Context) param.args[0] : null;
                                boot(ctx, loader);
                            } catch (Throwable t) {
                                Td.log("Instrumentation.callApplicationOnCreate hook 回调失败: " + t);
                            }
                        }
                    });
            Td.log("已 hook Instrumentation.callApplicationOnCreate");
        } catch (Throwable t) {
            XposedBridge.log("[TGXSum] hook Instrumentation.callApplicationOnCreate 失败: " + t);
        }
    }

    private static void boot(Context ctx, ClassLoader loader) {
        if (ctx == null) {
            return;
        }
        if (!BOOTED.compareAndSet(false, true)) {
            return;
        }
        try {
            Context app = ctx.getApplicationContext();
            if (app == null) {
                app = ctx;
            }
            Td.install(loader, app);
            OverlayController.start(app);
            Td.log("模块初始化完成");
        } catch (Throwable t) {
            XposedBridge.log("[TGXSum] 初始化失败: " + t);
        }
    }
}
