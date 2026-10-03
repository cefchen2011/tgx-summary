package com.dsh.tgxsummary;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Flat, reflection-free view of a TdApi$Message, plus the parser that turns a
 * TdApi$Messages response into a chronological list.
 */
public final class Msg {

    public long id;
    public long date;
    public boolean outgoing;
    public long senderId;
    public boolean senderIsChat;
    public String senderName;
    public String text;

    /**
     * Parses a TdApi$Messages. TDLib returns newest first, so the list is
     * reversed to be chronological (oldest first).
     */
    public static List<Msg> fromMessages(Object messagesObj) {
        List<Msg> result = new ArrayList<Msg>();
        if (messagesObj == null) {
            return result;
        }
        Object raw = Td.get(messagesObj, "messages");
        if (!(raw instanceof Object[])) {
            return result;
        }
        Object[] array = (Object[]) raw;
        for (int i = 0; i < array.length; i++) {
            Msg parsed = fromMessage(array[i]);
            if (parsed != null) {
                result.add(parsed);
            }
        }
        Collections.reverse(result);
        return result;
    }

    private static Msg fromMessage(Object messageObj) {
        if (messageObj == null) {
            return null;
        }
        Msg msg = new Msg();
        msg.id = Td.getLong(messageObj, "id");
        msg.date = Td.getInt(messageObj, "date");
        msg.outgoing = Td.getBool(messageObj, "isOutgoing");

        Object sender = Td.get(messageObj, "senderId");
        if (sender != null) {
            String senderClass = sender.getClass().getName();
            if (senderClass.contains("MessageSenderUser")) {
                msg.senderId = Td.getLong(sender, "userId");
            } else if (senderClass.contains("MessageSenderChat")) {
                msg.senderId = Td.getLong(sender, "chatId");
                msg.senderIsChat = true;
            }
        }

        msg.text = contentText(Td.get(messageObj, "content"));
        return msg;
    }

    /**
     * Extracts the readable text of a TdApi$MessageContent. Field access is
     * purely reflective because the content hierarchy has no shared accessor.
     */
    public static String contentText(Object content) {
        if (content == null) {
            return "";
        }
        String placeholder = placeholderFor(content.getClass().getName());

        String text = null;
        Object caption = Td.get(content, "caption");
        if (caption != null) {
            text = formattedText(Td.get(caption, "text"));
        }
        if (isBlank(text)) {
            text = formattedText(Td.get(content, "text"));
        }
        if (isBlank(text)) {
            text = formattedText(Td.get(content, "question"));
        }
        if (text == null) {
            text = "";
        }

        if (placeholder.length() > 0) {
            return text.length() > 0 ? (text + " " + placeholder) : placeholder;
        }
        return text;
    }

    private static String formattedText(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String) {
            return (String) value;
        }
        return Td.getStr(value, "text");
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    /**
     * Chinese placeholder for non-text content. More specific classes must be
     * tested before more generic ones (VideoNote before Video, VoiceNote before
     * Video-less names, and MessageText last).
     */
    private static String placeholderFor(String className) {
        if (className == null) {
            return "";
        }
        if (className.contains("VideoNote")) {
            return "[视频消息]";
        }
        if (className.contains("VoiceNote")) {
            return "[语音]";
        }
        if (className.contains("Video")) {
            return "[视频]";
        }
        if (className.contains("Audio")) {
            return "[音频]";
        }
        if (className.contains("Animation")) {
            return "[动图]";
        }
        if (className.contains("Sticker")) {
            return "[贴纸]";
        }
        if (className.contains("Photo")) {
            return "[图片]";
        }
        if (className.contains("Document")) {
            return "[文件]";
        }
        if (className.contains("Poll")) {
            return "[投票]";
        }
        if (className.contains("Location")) {
            return "[位置]";
        }
        if (className.contains("Venue")) {
            return "[位置]";
        }
        if (className.contains("Contact")) {
            return "[联系人]";
        }
        if (className.contains("Call")) {
            return "[通话]";
        }
        if (className.contains("Dice")) {
            return "[骰子]";
        }
        if (className.contains("Story")) {
            return "[故事]";
        }
        if (className.contains("Game")) {
            return "[游戏]";
        }
        if (className.contains("Invoice")) {
            return "[账单]";
        }
        if (className.contains("Unsupported")) {
            return "[不支持的消息]";
        }
        if (className.contains("Text")) {
            return "";
        }
        return "";
    }
}
