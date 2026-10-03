package com.dsh.tgxsummary;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Minimal, dependency-free client for the DeepSeek chat completions API.
 * Uses only the org.json bundled with Android and java.net.HttpURLConnection.
 */
public final class DeepSeekClient {

    public static final String DEFAULT_BASE_URL = "https://api.deepseek.com";
    public static final String DEFAULT_MODEL = "deepseek-chat";

    private static final String CHAT_PATH = "/chat/completions";
    private static final int CONNECT_TIMEOUT_MS = 20000;
    private static final int READ_TIMEOUT_MS = 120000;
    private static final int MAX_ERROR_BODY_CHARS = 600;

    private final String baseUrl;
    private final String apiKey;
    private final String model;

    public DeepSeekClient(String baseUrl, String apiKey, String model) {
        this.baseUrl = normalizeBaseUrl(baseUrl);
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = (model == null || model.trim().isEmpty()) ? DEFAULT_MODEL : model.trim();
    }

    public String chat(String systemPrompt, String userPrompt, int maxTokens, double temperature) throws Exception {
        if (apiKey == null || apiKey.isEmpty()) {
            throw new DeepSeekException(-1, "API Key not configured / 未配置");
        }

        JSONObject payload = new JSONObject();
        payload.put("model", model);

        JSONArray messages = new JSONArray();
        JSONObject systemMessage = new JSONObject();
        systemMessage.put("role", "system");
        systemMessage.put("content", systemPrompt == null ? "" : systemPrompt);
        messages.put(systemMessage);

        JSONObject userMessage = new JSONObject();
        userMessage.put("role", "user");
        userMessage.put("content", userPrompt == null ? "" : userPrompt);
        messages.put(userMessage);

        payload.put("messages", messages);
        payload.put("stream", false);
        payload.put("max_tokens", maxTokens);
        payload.put("temperature", temperature);

        HttpURLConnection connection = null;
        try {
            URL url = new URL(baseUrl + CHAT_PATH);
            Td.log("DeepSeek: 开始请求 " + url + " model=" + model + " maxTokens=" + maxTokens
                    + " keyLen=" + apiKey.length());
            connection = (HttpURLConnection) url.openConnection();
            Td.log("DeepSeek: openConnection 完成");
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setDoOutput(true);
            connection.setUseCaches(false);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("Authorization", "Bearer " + apiKey);

            byte[] body = payload.toString().getBytes(StandardCharsets.UTF_8);
            OutputStream output = connection.getOutputStream();
            try {
                output.write(body);
                output.flush();
            } finally {
                closeQuietly(output);
            }

            Td.log("DeepSeek: 请求体已发送，等待响应…");
            int status = connection.getResponseCode();
            Td.log("DeepSeek: HTTP " + status);
            if (status < 200 || status >= 300) {
                String errorBody = readAll(connection.getErrorStream());
                throw new DeepSeekException(status, "HTTP " + status + ": " + truncate(errorBody, MAX_ERROR_BODY_CHARS));
            }

            String responseBody = readAll(connection.getInputStream());
            Td.log("DeepSeek: 响应体长度 " + (responseBody == null ? 0 : responseBody.length()));
            return extractContent(responseBody, status);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    public String test() throws Exception {
        return chat("你是一个连通性测试助手，请用简体中文简短回答。", "ping", 8, 0.0);
    }

    private static String extractContent(String responseBody, int status) throws DeepSeekException {
        JSONObject response;
        try {
            response = new JSONObject(responseBody == null ? "" : responseBody);
        } catch (Exception parseFailure) {
            throw new DeepSeekException(status, "无法解析接口返回内容 / unparsable response body");
        }

        JSONArray choices = response.optJSONArray("choices");
        if (choices == null || choices.length() == 0) {
            throw new DeepSeekException(status, "接口返回中没有 choices / no choices in response");
        }

        JSONObject firstChoice = choices.optJSONObject(0);
        if (firstChoice == null) {
            throw new DeepSeekException(status, "choices[0] 不是对象 / choices[0] is not an object");
        }

        JSONObject message = firstChoice.optJSONObject("message");
        if (message == null) {
            throw new DeepSeekException(status, "choices[0].message 缺失 / missing message");
        }

        if (message.isNull("content")) {
            return "";
        }
        String content = message.optString("content", "");
        return content == null ? "" : content;
    }

    private static String normalizeBaseUrl(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty()) {
            value = DEFAULT_BASE_URL;
        }
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    private static String readAll(InputStream input) {
        if (input == null) {
            return "";
        }
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[4096];
            int read;
            while ((read = input.read(chunk)) != -1) {
                buffer.write(chunk, 0, read);
            }
            return new String(buffer.toByteArray(), StandardCharsets.UTF_8);
        } catch (Exception readFailure) {
            return "";
        } finally {
            closeQuietly(input);
        }
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.length() <= max) {
            return trimmed;
        }
        return trimmed.substring(0, max);
    }

    private static void closeQuietly(Closeable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (Exception ignored) {
            // Nothing useful can be done while closing.
        }
    }

    public static final class DeepSeekException extends Exception {

        private static final long serialVersionUID = 1L;

        public final int httpCode;

        public DeepSeekException(int httpCode, String message) {
            super(message);
            this.httpCode = httpCode;
        }
    }
}
