package org.example.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.example.util.HttpUtils;

import java.io.IOException;
import java.nio.file.Path;

public class PageHandler implements HttpHandler {
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        if ("/".equals(path) || "/index.html".equals(path)) {
            byte[] html = HttpUtils.readClasspathOrFile("/static/index.html", Path.of("src/main/resources/static/index.html"));
            HttpUtils.sendStatic(exchange, 200, html, "text/html; charset=UTF-8");
            return;
        }
        if ("/static/styles.css".equals(path)) {
            byte[] css = HttpUtils.readClasspathOrFile("/static/styles.css", Path.of("src/main/resources/static/styles.css"));
            HttpUtils.sendStatic(exchange, 200, css, "text/css; charset=UTF-8");
            return;
        }
        if ("/static/app.js".equals(path)) {
            byte[] js = HttpUtils.readClasspathOrFile("/static/app.js", Path.of("src/main/resources/static/app.js"));
            HttpUtils.sendStatic(exchange, 200, js, "application/javascript; charset=UTF-8");
            return;
        }
        HttpUtils.sendHtml(exchange, 404, "<h1>404 Not Found</h1>");
    }
}
