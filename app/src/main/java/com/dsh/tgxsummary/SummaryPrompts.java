package com.dsh.tgxsummary;

import java.util.Locale;

/**
 * Builds the summarization prompts and the per-level generation settings.
 */
public final class SummaryPrompts {

    private static final String LEVEL_SHORT = "l";
    private static final String LEVEL_STANDARD = "m";
    private static final String LEVEL_DETAILED = "h";

    private static final String NAME_SHORT = "简洁";
    private static final String NAME_STANDARD = "标准";
    private static final String NAME_DETAILED = "详细";

    private static final String FENCE = "--------";

    private SummaryPrompts() {
    }

    public static String systemPrompt(String level) {
        String normalized = normalize(level);
        StringBuilder builder = new StringBuilder();
        builder.append("你是一名聊天记录摘要助手，只负责把聊天记录整理成中文摘要。\n");
        builder.append("必须严格遵守以下规则：\n");
        builder.append("1. 只能输出简体中文。\n");
        builder.append("2. 只能使用聊天记录中真实出现的信息，绝对不要编造或补充人名、数字、决定或链接。\n");
        builder.append("3. 如果聊天记录为空或内容过短、不足以总结，就直接说明“聊天记录为空或过短，无法总结”，不要臆测。\n");
        builder.append("4. 提到谁说了什么时，必须使用聊天记录中真实出现的参与者名字，不要杜撰称呼。\n");

        if (LEVEL_SHORT.equals(normalized)) {
            builder.append("输出要求：用 1 到 2 句话概括，不要使用列表，总长度控制在约 80 个汉字以内。\n");
        } else if (LEVEL_DETAILED.equals(normalized)) {
            builder.append("输出要求：写成分段的详细摘要，依次使用以下三个标题：\n");
            builder.append("背景：\n");
            builder.append("讨论要点：\n");
            builder.append("结论与待办：\n");
            builder.append("每个部分都用“- ”开头的要点来写，并明确标注是谁说的，例如“张三：……”，内容可以写得比较长。\n");
        } else {
            builder.append("输出要求：先写 3 到 6 条要点，每条以“- ”开头；然后另起一行，以“结论：”开头写最后一句总结。\n");
        }
        return builder.toString();
    }

    public static String userPrompt(String chatTitle, String transcript, String level) {
        String normalized = normalize(level);
        String title = (chatTitle == null || chatTitle.trim().isEmpty()) ? "（未命名聊天）" : chatTitle.trim();
        String body = transcript == null ? "" : transcript;

        StringBuilder builder = new StringBuilder();
        builder.append("聊天标题：").append(title).append("\n");
        builder.append("摘要详细程度：").append(levelName(normalized)).append("\n");
        builder.append("\n");
        builder.append("以下是聊天记录：\n");
        builder.append(FENCE).append("\n");
        builder.append(body).append("\n");
        builder.append(FENCE).append("\n");
        builder.append("\n");
        builder.append("请仅根据上面两行 ").append(FENCE).append(" 之间的聊天记录进行总结：\n");
        builder.append("不要编造任何没有出现在聊天记录中的人名、数字、决定或链接；");
        builder.append("如果记录为空或过短，请直接说明，不要臆测。\n");
        return builder.toString();
    }

    public static String formatMessage(String time, String sender, String text) {
        String timeValue = time == null ? "" : time.trim();
        String senderValue = sender == null ? "" : sender.trim();
        String textValue = text == null ? "" : text;
        if (timeValue.isEmpty()) {
            return senderValue + ": " + textValue;
        }
        return "[" + timeValue + "] " + senderValue + ": " + textValue;
    }

    public static int maxTokensFor(String level) {
        String normalized = normalize(level);
        if (LEVEL_SHORT.equals(normalized)) {
            return 400;
        }
        if (LEVEL_DETAILED.equals(normalized)) {
            return 3000;
        }
        return 1200;
    }

    public static double temperatureFor(String level) {
        String normalized = normalize(level);
        if (LEVEL_SHORT.equals(normalized)) {
            return 0.2;
        }
        if (LEVEL_DETAILED.equals(normalized)) {
            return 0.6;
        }
        return 0.4;
    }

    private static String normalize(String level) {
        if (level == null) {
            return LEVEL_STANDARD;
        }
        String value = level.trim().toLowerCase(Locale.ROOT);
        if (LEVEL_SHORT.equals(value) || LEVEL_DETAILED.equals(value)) {
            return value;
        }
        return LEVEL_STANDARD;
    }

    private static String levelName(String normalized) {
        if (LEVEL_SHORT.equals(normalized)) {
            return NAME_SHORT;
        }
        if (LEVEL_DETAILED.equals(normalized)) {
            return NAME_DETAILED;
        }
        return NAME_STANDARD;
    }
}
