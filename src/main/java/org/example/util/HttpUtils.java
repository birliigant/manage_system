package org.example.util;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public final class HttpUtils {
    private HttpUtils() {
    }

    public static String readBody(HttpExchange exchange) throws IOException {
        try (InputStream inputStream = exchange.getRequestBody();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            inputStream.transferTo(outputStream);
            return outputStream.toString(StandardCharsets.UTF_8);
        }
    }

    public static void sendJson(HttpExchange exchange, int status, Map<String, Object> body) throws IOException {
        sendBytes(exchange, status, JsonUtils.stringify(body).getBytes(StandardCharsets.UTF_8), "application/json; charset=UTF-8");
    }

    public static void sendHtml(HttpExchange exchange, int status, String html) throws IOException {
        sendBytes(exchange, status, html.getBytes(StandardCharsets.UTF_8), "text/html; charset=UTF-8");
    }

    public static void sendStatic(HttpExchange exchange, int status, byte[] bytes, String contentType) throws IOException {
        sendBytes(exchange, status, bytes, contentType);
    }

    public static void sendNoContent(HttpExchange exchange) throws IOException {
        Headers headers = exchange.getResponseHeaders();
        addCorsHeaders(headers);
        exchange.sendResponseHeaders(204, -1);
        exchange.close();
    }

    public static Map<String, Object> ok(Object data) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("message", "OK");
        response.put("data", data);
        return response;
    }

    public static Map<String, Object> error(String message) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", false);
        response.put("message", message);
        response.put("data", null);
        return response;
    }

    public static byte[] readClasspathOrFile(String classpathLocation, Path fallbackPath) throws IOException {
        try (InputStream inputStream = HttpUtils.class.getResourceAsStream(classpathLocation)) {
            if (inputStream != null) {
                return inputStream.readAllBytes();
            }
        }
        return Files.readAllBytes(fallbackPath);
    }

    private static void sendBytes(HttpExchange exchange, int status, byte[] bytes, String contentType) throws IOException {
        Headers headers = exchange.getResponseHeaders();
        addCorsHeaders(headers);
        headers.set("Content-Type", contentType);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(bytes);
        }
    }

    private static void addCorsHeaders(Headers headers) {
        headers.set("Access-Control-Allow-Origin", "*");
        headers.set("Access-Control-Allow-Methods", "GET,POST,PUT,DELETE,OPTIONS");
        headers.set("Access-Control-Allow-Headers", "Content-Type");
    }
}
