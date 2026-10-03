package com.dsh.tgxsummary;

import android.content.Context;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * Thin reflective bridge to the TDLib Java bindings that Telegram X embeds.
 *
 * Telegram X's own classes are obfuscated by R8, but org.drinkless.tdlib.TdApi
 * and org.drinkless.tdlib.Client keep their names. The public ResultHandler
 * interface of Client *is* obfuscated, so it is never looked up by name: it is
 * discovered as the second parameter type of Client.send.
 */
public final class Td {

    public static final String TAG = "TGXSum";
    public static final String TGX = "org.thunderdog.challegram";

    private static final String CLIENT_CLASS_NAME = "org.drinkless.tdlib.Client";
    private static final String FUNCTION_CLASS_NAME = "org.drinkless.tdlib.TdApi$Function";
    private static final String TDAPI_PREFIX = "org.drinkless.tdlib.TdApi$";

    /** Chat id of the conversation the user is currently looking at, 0 when unknown. */
    public static volatile long currentChatId;

    /** Title of {@link #currentChatId}, may be null until resolved. */
    public static volatile String currentChatTitle;

    private static volatile ClassLoader cl;
    private static volatile Object client;
    private static volatile Method sendMethod;
    private static volatile Class<?> resultHandlerClass;

    /** Callback invoked on the TDLib response thread with a TdApi$Object. */
    public interface ResultHandler {
        void onResult(Object obj);
    }

    private Td() {
    }

    public static void log(String s) {
        try {
            XposedBridge.log("[" + TAG + "] " + s);
        } catch (Throwable ignored) {
            // XposedBridge may be unavailable outside of a hooked process.
        }
    }

    public static boolean ready() {
        return client != null && sendMethod != null;
    }

    public static void install(ClassLoader classLoader, Context app) {
        if (classLoader == null) {
            log("install 失败: classLoader 为 null");
            return;
        }
        cl = classLoader;
        try {
            final Class<?> clientClass = XposedHelpers.findClass(CLIENT_CLASS_NAME, classLoader);
            Class<?> functionClass = null;
            try {
                functionClass = XposedHelpers.findClass(FUNCTION_CLASS_NAME, classLoader);
            } catch (Throwable t) {
                log("找不到 " + FUNCTION_CLASS_NAME + ": " + t);
            }
            if (functionClass == null) {
                log("install 终止: 缺少 TdApi$Function，无法定位 send");
                return;
            }

            Method foundSend = null;
            Class<?> foundHandler = null;
            Method[] methods = clientClass.getDeclaredMethods();
            for (int i = 0; i < methods.length; i++) {
                Method candidate = methods[i];
                Class<?>[] params = candidate.getParameterTypes();
                if (params.length != 2) {
                    continue;
                }
                if (params[0] != functionClass) {
                    continue;
                }
                if (!params[1].isInterface()) {
                    continue;
                }
                if (candidate.getReturnType() != void.class) {
                    continue;
                }
                foundSend = candidate;
                foundHandler = params[1];
                break;
            }

            if (foundSend == null || foundHandler == null) {
                log("install 失败: " + CLIENT_CLASS_NAME + " 中未找到 send(Function, interface)");
                return;
            }

            sendMethod = foundSend;
            resultHandlerClass = foundHandler;
            log("install: 找到 send = " + foundSend.getName()
                    + ", resultHandler = " + foundHandler.getName()
                    + ", 参数 = " + foundSend.getParameterTypes()[0].getName());

            try {
                XposedBridge.hookMethod(foundSend, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        try {
                            client = param.thisObject;
                            if (param.args != null && param.args.length > 0) {
                                observeFunction(param.args[0]);
                            }
                        } catch (Throwable t) {
                            log("send hook 异常: " + t);
                        }
                    }
                });
                log("install: 已 hook send");
            } catch (Throwable t) {
                log("hook send 失败: " + t);
            }

            try {
                XposedBridge.hookAllConstructors(clientClass, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        try {
                            client = param.thisObject;
                        } catch (Throwable t) {
                            log("Client 构造器 hook 异常: " + t);
                        }
                    }
                });
                log("install: 已 hook Client 全部构造器");
            } catch (Throwable t) {
                log("hook Client 构造器失败: " + t);
            }
        } catch (Throwable t) {
            log("install 失败: " + t);
        }
    }

    /**
     * Tracks which chat the user is currently browsing by watching the TDLib
     * requests Telegram X sends. Only reliable, chat scoped functions are used.
     */
    public static void observeFunction(Object fn) {
        if (fn == null) {
            return;
        }
        try {
            String className = fn.getClass().getName();
            if (className.endsWith("$OpenChat")) {
                currentChatId = getLong(fn, "chatId");
                currentChatTitle = null;
            } else if (className.endsWith("$CloseChat")) {
                if (getLong(fn, "chatId") == currentChatId) {
                    currentChatId = 0L;
                }
            } else if (className.endsWith("$ViewMessages")
                    || className.endsWith("$GetChatHistory")
                    || className.endsWith("$GetChatMessageByDate")
                    || className.endsWith("$GetChatMessagePosition")
                    || className.endsWith("$GetChatPinnedMessage")) {
                long chatId = getLong(fn, "chatId");
                if (chatId != 0L) {
                    currentChatId = chatId;
                }
            }
        } catch (Throwable t) {
            log("observeFunction 异常: " + t);
        }
    }

    public static void send(final Object function, final ResultHandler handler) {
        final Object instance = client;
        final Method method = sendMethod;
        final Class<?> handlerClass = resultHandlerClass;
        if (instance == null || method == null || handlerClass == null) {
            throw new IllegalStateException("TDLib 未就绪");
        }

        ClassLoader loader = cl != null ? cl : handlerClass.getClassLoader();
        Object proxy = Proxy.newProxyInstance(loader, new Class<?>[]{handlerClass}, new InvocationHandler() {
            @Override
            public Object invoke(Object proxyObject, Method proxyMethod, Object[] args) throws Throwable {
                try {
                    if (handler != null) {
                        Object result = (args != null && args.length > 0) ? args[0] : null;
                        handler.onResult(result);
                    }
                } catch (Throwable t) {
                    log("ResultHandler 回调异常: " + t);
                }
                return null;
            }
        });

        try {
            method.setAccessible(true);
            method.invoke(instance, function, proxy);
        } catch (Throwable t) {
            Throwable cause = t;
            if (t instanceof InvocationTargetException && t.getCause() != null) {
                cause = t.getCause();
            }
            log("send 调用失败: " + cause);
            throw new IllegalStateException("TDLib 请求发送失败: " + cause, cause);
        }
    }

    public static Object request(Object function, long timeoutMs) throws Exception {
        if (!ready()) {
            throw new IllegalStateException("TDLib 未就绪");
        }
        final CountDownLatch latch = new CountDownLatch(1);
        final Object[] holder = new Object[1];

        send(function, new ResultHandler() {
            @Override
            public void onResult(Object obj) {
                holder[0] = obj;
                latch.countDown();
            }
        });

        boolean completed;
        try {
            completed = latch.await(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new Exception("TDLib 请求被中断");
        }
        if (!completed) {
            throw new Exception("TDLib 请求超时");
        }
        return holder[0];
    }

    /**
     * Builds a TdApi$&lt;simpleName&gt; instance by matching its public constructor
     * against the supplied boxed arguments.
     */
    public static Object fnAuto(String simpleName, Object... args) throws Exception {
        if (cl == null) {
            throw new Exception("TDLib ClassLoader 未就绪");
        }
        Class<?> target;
        try {
            target = Class.forName(TDAPI_PREFIX + simpleName, true, cl);
        } catch (Throwable t) {
            throw new Exception("找不到 TdApi 类: " + simpleName);
        }

        Object[] callArgs = args == null ? new Object[0] : args;
        int wanted = callArgs.length;
        Constructor<?> match = null;
        Constructor<?>[] constructors = target.getDeclaredConstructors();
        for (int i = 0; i < constructors.length; i++) {
            Constructor<?> candidate = constructors[i];
            Class<?>[] types = candidate.getParameterTypes();
            if (types.length != wanted) {
                continue;
            }
            boolean compatible = true;
            for (int j = 0; j < wanted; j++) {
                if (!matches(types[j], callArgs[j])) {
                    compatible = false;
                    break;
                }
            }
            if (compatible) {
                match = candidate;
                break;
            }
        }
        if (match == null) {
            throw new Exception("找不到构造器: " + target.getName() + " / " + wanted + " 个参数");
        }

        try {
            match.setAccessible(true);
            return match.newInstance(callArgs);
        } catch (Throwable t) {
            Throwable cause = t;
            if (t instanceof InvocationTargetException && t.getCause() != null) {
                cause = t.getCause();
            }
            throw new Exception("创建 " + simpleName + " 失败: " + cause);
        }
    }

    private static boolean matches(Class<?> type, Object value) {
        if (value == null) {
            return !type.isPrimitive();
        }
        if (!type.isPrimitive()) {
            return type.isInstance(value);
        }
        if (type == int.class) {
            return value instanceof Integer;
        }
        if (type == long.class) {
            return value instanceof Long;
        }
        if (type == boolean.class) {
            return value instanceof Boolean;
        }
        if (type == double.class) {
            return value instanceof Double;
        }
        if (type == float.class) {
            return value instanceof Float;
        }
        if (type == short.class) {
            return value instanceof Short;
        }
        if (type == byte.class) {
            return value instanceof Byte;
        }
        if (type == char.class) {
            return value instanceof Character;
        }
        return false;
    }

    public static Object get(Object o, String field) {
        if (o == null || field == null) {
            return null;
        }
        try {
            return XposedHelpers.getObjectField(o, field);
        } catch (Throwable t) {
            return null;
        }
    }

    public static long getLong(Object o, String field) {
        if (o == null || field == null) {
            return 0L;
        }
        try {
            return XposedHelpers.getLongField(o, field);
        } catch (Throwable t) {
            return 0L;
        }
    }

    public static int getInt(Object o, String field) {
        if (o == null || field == null) {
            return 0;
        }
        try {
            return XposedHelpers.getIntField(o, field);
        } catch (Throwable t) {
            return 0;
        }
    }

    public static boolean getBool(Object o, String field) {
        if (o == null || field == null) {
            return false;
        }
        try {
            return XposedHelpers.getBooleanField(o, field);
        } catch (Throwable t) {
            return false;
        }
    }

    public static String getStr(Object o, String field) {
        if (o == null || field == null) {
            return null;
        }
        try {
            Object value = XposedHelpers.getObjectField(o, field);
            return value instanceof String ? (String) value : null;
        } catch (Throwable t) {
            return null;
        }
    }
}
