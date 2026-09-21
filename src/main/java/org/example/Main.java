package org.example;

import com.sun.net.httpserver.HttpServer;
import org.example.db.DatabaseManager;
import org.example.security.AuthService;
import org.example.service.DecorationManagementService;
import org.example.web.ApiHandler;
import org.example.web.PageHandler;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

public class Main {
    public static void main(String[] args) throws IOException {
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));

        DatabaseManager databaseManager = new DatabaseManager();
        DecorationManagementService service = new DecorationManagementService(databaseManager);
        AuthService authService = new AuthService(databaseManager);
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/api", new ApiHandler(service, authService));
        server.createContext("/", new PageHandler());
        server.setExecutor(Executors.newFixedThreadPool(8));
        server.start();

        System.out.println("Decoration Company Management System started.");
        System.out.println("Open http://localhost:" + port + "/");
    }
}
