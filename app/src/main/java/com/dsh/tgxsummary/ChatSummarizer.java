package com.dsh.tgxsummary;

import android.content.Context;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

/**
 * Reads the most recent messages of one chat, builds a transcript and asks
 * DeepSeek for a summary. Everything runs on a dedicated worker thread; all
 * TDLib calls are synchronous requests through {@link Td#request}.
 */
public final class ChatSummarizer {

    public interface Callback {
        void onProgress(String status);

        void onDone(String summary);

        void onError(String message);
    }

    private static final String WORKER_NAME = "tgxsum-work";
    private static final long HISTORY_TIMEOUT_MS = 20000L;
    private static final long USER_TIMEOUT_MS = 12000L;
    private static final long CHAT_TIMEOUT_MS = 12000L;
    private static final String FALLBACK_TITLE = "当前会话";
    private static final String FALLBACK_SENDER = "未知";

    private static final HashMap<Long, String> USER_NAMES = new HashMap<Long, String>();

    private ChatSummarizer() {
    }

    public static void run(final Context ctx, final long chatId, final int count, final String level, final Callback cb) {
        Td.log("ChatSummarizer.run chatId=" + chatId + " count=" + count + " level=" + level
                + " tdReady=" + Td.ready());
        Thread worker = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    doRun(ctx, chatId, count, level, cb);
                } catch (Throwable t) {
                    Td.log("ChatSummarizer 失败: " + t);
                    if (cb != null) {
                        cb.onError("总结失败: " + t);
                    }
                }
            }
        }, WORKER_NAME);
        worker.setDaemon(true);
        worker.start();
    }

    private static void doRun(Context ctx, long chatId, int count, String level, Callback cb) throws Exception {
        if (cb == null) {
            throw new Exception("内部错误: 回调为空");
        }
        if (!Td.ready()) {
            throw new Exception("TDLib 未就绪，请重启 Telegram X");
        }
        if (chatId == 0L) {
            throw new Exception("未识别到当前会话，请先打开一个聊天再试");
        }
        if (count <= 0) {
            count = Config.DEF_COUNT;
        }

        Td.log("ChatSummarizer 开始读取历史 chatId=" + chatId + " limit=" + count);
        cb.onProgress("正在读取最近 " + count + " 条消息…");

        Object fn = Td.fnAuto("GetChatHistory",
                Long.valueOf(chatId),
                Long.valueOf(0L),
                Integer.valueOf(0),
                Integer.valueOf(count),
                Boolean.FALSE);
        Object res = Td.request(fn, HISTORY_TIMEOUT_MS);
        if (res != null && res.getClass().getName().contains("Error")) {
            String message = Td.getStr(res, "message");
            throw new Exception("读取消息失败: " + (message == null ? "未知错误" : message));
        }

        List<Msg> msgs = Msg.fromMessages(res);
        if (msgs.isEmpty()) {
            throw new Exception("该会话没有可读取的消息");
        }

        cb.onProgress("正在解析发送者…");
        for (int i = 0; i < msgs.size(); i++) {
            Msg msg = msgs.get(i);
            if (msg.senderIsChat || msg.senderId == 0L) {
                continue;
            }
            msg.senderName = resolveUserName(msg.senderId);
        }

        String title = resolveChatTitle(chatId);

        SimpleDateFormat format = new SimpleDateFormat("HH:mm", Locale.CHINA);
        StringBuilder transcript = new StringBuilder();
        for (int i = 0; i < msgs.size(); i++) {
            Msg msg = msgs.get(i);
            String time = format.format(new Date(msg.date * 1000L));
            String who;
            if (msg.outgoing) {
                who = "我";
            } else if (msg.senderName == null || msg.senderName.trim().isEmpty()) {
                who = FALLBACK_SENDER;
            } else {
                who = msg.senderName;
            }
            transcript.append(SummaryPrompts.formatMessage(time, who, msg.text)).append("\n");
        }

        // 优先使用 Telegram X 进程内的本地配置（同进程读取，最可靠）；
        // 只有在本地没有配置时才去走跨进程兜底。
        String apiKey;
        String baseUrl;
        String model;
        String localKey = TgPrefs.apiKey(ctx);
        if (localKey != null && !localKey.isEmpty()) {
            apiKey = localKey;
            baseUrl = TgPrefs.baseUrl(ctx);
            model = TgPrefs.model(ctx);
            Td.log("ChatSummarizer: 使用进程内配置 apiKeyLen=" + apiKey.length());
        } else {
            HookConfig cfg = HookConfig.load(ctx);
            apiKey = cfg.apiKey;
            baseUrl = cfg.baseUrl;
            model = cfg.model;
            Td.log("ChatSummarizer: 使用模块 App 配置 apiKeyLen=" + (apiKey == null ? 0 : apiKey.length()));
        }
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new Exception("未配置 DeepSeek API Key（请在「TGX 消息总结」App 里填写并保存，或在叠加层设置里填写）");
        }

        cb.onProgress("正在请求 DeepSeek…");
        DeepSeekClient deepSeek = new DeepSeekClient(baseUrl, apiKey.trim(), model);
        String result = deepSeek.chat(
                SummaryPrompts.systemPrompt(level),
                SummaryPrompts.userPrompt(title, transcript.toString(), level),
                SummaryPrompts.maxTokensFor(level),
                SummaryPrompts.temperatureFor(level));

        if (result == null || result.trim().isEmpty()) {
            throw new Exception("DeepSeek 没有返回内容");
        }
        Td.log("ChatSummarizer 成功，摘要长度=" + result.trim().length());
        cb.onDone(result.trim());
    }

    private static String resolveUserName(long userId) {
        Long key = Long.valueOf(userId);
        synchronized (USER_NAMES) {
            String cached = USER_NAMES.get(key);
            if (cached != null) {
                return cached;
            }
        }

        String name = null;
        try {
            Object user = Td.request(Td.fnAuto("GetUser", key), USER_TIMEOUT_MS);
            if (user != null && !user.getClass().getName().contains("Error")) {
                String first = Td.getStr(user, "firstName");
                String last = Td.getStr(user, "lastName");
                StringBuilder builder = new StringBuilder();
                if (first != null && !first.trim().isEmpty()) {
                    builder.append(first.trim());
                }
                if (last != null && !last.trim().isEmpty()) {
                    if (builder.length() > 0) {
                        builder.append(' ');
                    }
                    builder.append(last.trim());
                }
                if (builder.length() > 0) {
                    name = builder.toString();
                }
            }
        } catch (Throwable t) {
            Td.log("解析用户 " + userId + " 失败: " + t);
        }

        if (name == null || name.trim().isEmpty()) {
            name = "用户" + userId;
        }
        synchronized (USER_NAMES) {
            USER_NAMES.put(key, name);
        }
        return name;
    }

    private static String resolveChatTitle(long chatId) {
        String title = Td.currentChatTitle;
        if (title != null && !title.trim().isEmpty()) {
            return title;
        }
        try {
            Object chat = Td.request(Td.fnAuto("GetChat", Long.valueOf(chatId)), CHAT_TIMEOUT_MS);
            if (chat != null && !chat.getClass().getName().contains("Error")) {
                String value = Td.getStr(chat, "title");
                if (value != null && !value.trim().isEmpty()) {
                    title = value;
                    Td.currentChatTitle = value;
                }
            }
        } catch (Throwable t) {
            Td.log("获取会话标题失败: " + t);
        }
        if (title == null || title.trim().isEmpty()) {
            title = FALLBACK_TITLE;
        }
        return title;
    }

}
