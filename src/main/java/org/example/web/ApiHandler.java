package org.example.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.example.security.AuthService;
import org.example.security.AuthUser;
import org.example.service.DecorationManagementService;
import org.example.util.AppException;
import org.example.util.HttpUtils;
import org.example.util.JsonUtils;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

public class ApiHandler implements HttpHandler {
    private final DecorationManagementService service;
    private final AuthService authService;

    public ApiHandler(DecorationManagementService service, AuthService authService) {
        this.service = service;
        this.authService = authService;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                HttpUtils.sendNoContent(exchange);
                return;
            }

            String method = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getPath();

            if (routeAuth(exchange, method, path)) {
                return;
            }

            AuthUser currentUser = authService.requireUser(exchange);

            if ("/api/dashboard/summary".equals(path) && "GET".equalsIgnoreCase(method)) {
                authService.requireReadPermission(currentUser, "dashboard");
                HttpUtils.sendJson(exchange, 200, HttpUtils.ok(service.dashboardSummary()));
                return;
            }
            if ("/api/options".equals(path) && "GET".equalsIgnoreCase(method)) {
                Map<String, Object> result = service.options();
                filterOptionsByPermission(result, currentUser);
                result.put("currentUser", authService.currentUser(exchange));
                result.put("permissionOverview", authService.permissionOverview(currentUser));
                HttpUtils.sendJson(exchange, 200, HttpUtils.ok(result));
                return;
            }
            if ("/api/auth/permissions".equals(path) && "GET".equalsIgnoreCase(method)) {
                HttpUtils.sendJson(exchange, 200, HttpUtils.ok(authService.permissionOverview(currentUser)));
                return;
            }

            if (routeCustomers(exchange, currentUser, method, path)
                    || routeEmployees(exchange, currentUser, method, path)
                    || routeUsers(exchange, currentUser, method, path)
                    || routeProjects(exchange, currentUser, method, path)
                    || routeStages(exchange, currentUser, method, path)
                    || routeSuppliers(exchange, currentUser, method, path)
                    || routeMaterials(exchange, currentUser, method, path)
                    || routePayments(exchange, currentUser, method, path)) {
                return;
            }

            throw new AppException(404, "接口不存在。");
        } catch (AppException ex) {
            HttpUtils.sendJson(exchange, ex.status(), HttpUtils.error(ex.getMessage()));
        } catch (Exception ex) {
            HttpUtils.sendJson(exchange, 500, HttpUtils.error("服务器内部错误: " + ex.getMessage()));
        }
    }

    private boolean routeAuth(HttpExchange exchange, String method, String path) throws IOException {
        if ("/api/auth/login".equals(path) && "POST".equalsIgnoreCase(method)) {
            Map<String, Object> payload = parseJsonBody(exchange);
            HttpUtils.sendJson(exchange, 200, HttpUtils.ok(authService.login(
                    stringValue(payload.get("username")),
                    stringValue(payload.get("password"))
            )));
            return true;
        }
        if ("/api/auth/me".equals(path) && "GET".equalsIgnoreCase(method)) {
            HttpUtils.sendJson(exchange, 200, HttpUtils.ok(authService.currentUser(exchange)));
            return true;
        }
        if ("/api/auth/logout".equals(path) && "POST".equalsIgnoreCase(method)) {
            authService.logout(exchange);
            HttpUtils.sendJson(exchange, 200, HttpUtils.ok(Map.of("loggedOut", true)));
            return true;
        }
        return false;
    }

    private boolean routeCustomers(HttpExchange exchange, AuthUser currentUser, String method, String path) throws IOException {
        if ("/api/customers".equals(path)) {
            if ("GET".equalsIgnoreCase(method)) {
                authService.requireReadPermission(currentUser, "customers");
                HttpUtils.sendJson(exchange, 200, HttpUtils.ok(service.listCustomers(queryParam(exchange, "keyword"))));
                return true;
            }
            if ("POST".equalsIgnoreCase(method)) {
                authService.requireWritePermission(currentUser, "customers");
                HttpUtils.sendJson(exchange, 201, HttpUtils.ok(service.createCustomer(parseJsonBody(exchange))));
                return true;
            }
        }
        Long id = extractId(path, "/api/customers/");
        if (id == null) {
            return false;
        }
        if ("GET".equalsIgnoreCase(method)) {
            authService.requireReadPermission(currentUser, "customers");
            HttpUtils.sendJson(exchange, 200, HttpUtils.ok(service.getCustomer(id)));
            return true;
        }
        if ("PUT".equalsIgnoreCase(method)) {
            authService.requireWritePermission(currentUser, "customers");
            HttpUtils.sendJson(exchange, 200, HttpUtils.ok(service.updateCustomer(id, parseJsonBody(exchange))));
            return true;
        }
        if ("DELETE".equalsIgnoreCase(method)) {
            authService.requireWritePermission(currentUser, "customers");
            service.deleteCustomer(id);
            HttpUtils.sendJson(exchange, 200, HttpUtils.ok(Map.of("deletedId", id)));
            return true;
        }
        return false;
    }

    private boolean routeEmployees(HttpExchange exchange, AuthUser currentUser, String method, String path) throws IOException {
        if ("/api/employees".equals(path)) {
            if ("GET".equalsIgnoreCase(method)) {
                authService.requireReadPermission(currentUser, "employees");
                HttpUtils.sendJson(exchange, 200, HttpUtils.ok(service.listEmployees(queryParam(exchange, "keyword"))));
                return true;
            }
            if ("POST".equalsIgnoreCase(method)) {
                authService.requireWritePermission(currentUser, "employees");
                HttpUtils.sendJson(exchange, 201, HttpUtils.ok(service.createEmployee(parseJsonBody(exchange))));
                return true;
            }
        }
        Long id = extractId(path, "/api/employees/");
        if (id == null) {
            return false;
        }
        if ("GET".equalsIgnoreCase(method)) {
            authService.requireReadPermission(currentUser, "employees");
            HttpUtils.sendJson(exchange, 200, HttpUtils.ok(service.getEmployee(id)));
            return true;
        }
        if ("PUT".equalsIgnoreCase(method)) {
            authService.requireWritePermission(currentUser, "employees");
            HttpUtils.sendJson(exchange, 200, HttpUtils.ok(service.updateEmployee(id, parseJsonBody(exchange))));
            return true;
        }
        if ("DELETE".equalsIgnoreCase(method)) {
            authService.requireWritePermission(currentUser, "employees");
            service.deleteEmployee(id);
            HttpUtils.sendJson(exchange, 200, HttpUtils.ok(Map.of("deletedId", id)));
            return true;
        }
        return false;
    }

    private boolean routeUsers(HttpExchange exchange, AuthUser currentUser, String method, String path) throws IOException {
        if ("/api/users".equals(path)) {
            if ("GET".equalsIgnoreCase(method)) {
                authService.requireReadPermission(currentUser, "users");
                HttpUtils.sendJson(exchange, 200, HttpUtils.ok(service.listUsers(queryParam(exchange, "keyword"))));
                return true;
            }
            if ("POST".equalsIgnoreCase(method)) {
                authService.requireWritePermission(currentUser, "users");
                HttpUtils.sendJson(exchange, 201, HttpUtils.ok(service.createUser(parseJsonBody(exchange))));
                return true;
            }
        }
        Long resetId = extractId(path, "/api/users/", "/reset-password");
        if (resetId != null && "POST".equalsIgnoreCase(method)) {
            authService.requireWritePermission(currentUser, "users");
            HttpUtils.sendJson(exchange, 200, HttpUtils.ok(service.resetUserPassword(resetId, parseJsonBody(exchange))));
            return true;
        }
        Long id = extractId(path, "/api/users/");
        if (id == null) {
            return false;
        }
        if ("GET".equalsIgnoreCase(method)) {
            authService.requireReadPermission(currentUser, "users");
            HttpUtils.sendJson(exchange, 200, HttpUtils.ok(service.getUser(id)));
            return true;
        }
        if ("PUT".equalsIgnoreCase(method)) {
            authService.requireWritePermission(currentUser, "users");
            HttpUtils.sendJson(exchange, 200, HttpUtils.ok(service.updateUser(id, parseJsonBody(exchange))));
            return true;
        }
        if ("DELETE".equalsIgnoreCase(method)) {
            authService.requireWritePermission(currentUser, "users");
            service.deleteUser(id);
            HttpUtils.sendJson(exchange, 200, HttpUtils.ok(Map.of("deletedId", id)));
            return true;
        }
        return false;
    }

    private boolean routeProjects(HttpExchange exchange, AuthUser currentUser, String method, String path) throws IOException {
        if ("/api/projects".equals(path)) {
            if ("GET".equalsIgnoreCase(method)) {
                authService.requireReadPermission(currentUser, "projects");
                HttpUtils.sendJson(exchange, 200, HttpUtils.ok(service.listProjects(queryParam(exchange, "keyword"), queryParam(exchange, "status"))));
                return true;
            }
            if ("POST".equalsIgnoreCase(method)) {
                authService.requireWritePermission(currentUser, "projects");
                HttpUtils.sendJson(exchange, 201, HttpUtils.ok(service.createProject(parseJsonBody(exchange))));
                return true;
            }
        }
        Long id = extractId(path, "/api/projects/");
        if (id == null) {
            return false;
        }
        if ("GET".equalsIgnoreCase(method)) {
            authService.requireReadPermission(currentUser, "projects");
            HttpUtils.sendJson(exchange, 200, HttpUtils.ok(service.getProject(id)));
            return true;
        }
        if ("PUT".equalsIgnoreCase(method)) {
            authService.requireWritePermission(currentUser, "projects");
            HttpUtils.sendJson(exchange, 200, HttpUtils.ok(service.updateProject(id, parseJsonBody(exchange))));
            return true;
        }
        if ("DELETE".equalsIgnoreCase(method)) {
            authService.requireWritePermission(currentUser, "projects");
            service.deleteProject(id);
            HttpUtils.sendJson(exchange, 200, HttpUtils.ok(Map.of("deletedId", id)));
            return true;
        }
        return false;
    }

    private boolean routeStages(HttpExchange exchange, AuthUser currentUser, String method, String path) throws IOException {
        if ("/api/stages".equals(path)) {
            if ("GET".equalsIgnoreCase(method)) {
                authService.requireReadPermission(currentUser, "stages");
                HttpUtils.sendJson(exchange, 200, HttpUtils.ok(service.listStages(optionalLong(queryParam(exchange, "projectId")))));
                return true;
            }
            if ("POST".equalsIgnoreCase(method)) {
                authService.requireWritePermission(currentUser, "stages");
                HttpUtils.sendJson(exchange, 201, HttpUtils.ok(service.createStage(parseJsonBody(exchange))));
                return true;
            }
        }
        Long id = extractId(path, "/api/stages/");
        if (id == null) {
            return false;
        }
        if ("GET".equalsIgnoreCase(method)) {
            authService.requireReadPermission(currentUser, "stages");
            HttpUtils.sendJson(exchange, 200, HttpUtils.ok(service.getStage(id)));
            return true;
        }
        if ("PUT".equalsIgnoreCase(method)) {
            authService.requireWritePermission(currentUser, "stages");
            HttpUtils.sendJson(exchange, 200, HttpUtils.ok(service.updateStage(id, parseJsonBody(exchange))));
            return true;
        }
        if ("DELETE".equalsIgnoreCase(method)) {
            authService.requireWritePermission(currentUser, "stages");
            service.deleteStage(id);
            HttpUtils.sendJson(exchange, 200, HttpUtils.ok(Map.of("deletedId", id)));
            return true;
        }
        return false;
    }

    private boolean routeSuppliers(HttpExchange exchange, AuthUser currentUser, String method, String path) throws IOException {
        if ("/api/suppliers".equals(path)) {
            if ("GET".equalsIgnoreCase(method)) {
                authService.requireReadPermission(currentUser, "suppliers");
                HttpUtils.sendJson(exchange, 200, HttpUtils.ok(service.listSuppliers(queryParam(exchange, "keyword"))));
                return true;
            }
            if ("POST".equalsIgnoreCase(method)) {
                authService.requireWritePermission(currentUser, "suppliers");
                HttpUtils.sendJson(exchange, 201, HttpUtils.ok(service.createSupplier(parseJsonBody(exchange))));
                return true;
            }
        }
        Long id = extractId(path, "/api/suppliers/");
        if (id == null) {
            return false;
        }
        if ("GET".equalsIgnoreCase(method)) {
            authService.requireReadPermission(currentUser, "suppliers");
            HttpUtils.sendJson(exchange, 200, HttpUtils.ok(service.getSupplier(id)));
            return true;
        }
        if ("PUT".equalsIgnoreCase(method)) {
            authService.requireWritePermission(currentUser, "suppliers");
            HttpUtils.sendJson(exchange, 200, HttpUtils.ok(service.updateSupplier(id, parseJsonBody(exchange))));
            return true;
        }
        if ("DELETE".equalsIgnoreCase(method)) {
            authService.requireWritePermission(currentUser, "suppliers");
            service.deleteSupplier(id);
            HttpUtils.sendJson(exchange, 200, HttpUtils.ok(Map.of("deletedId", id)));
            return true;
        }
        return false;
    }

    private boolean routeMaterials(HttpExchange exchange, AuthUser currentUser, String method, String path) throws IOException {
        if ("/api/materials".equals(path)) {
            if ("GET".equalsIgnoreCase(method)) {
                authService.requireReadPermission(currentUser, "materials");
                HttpUtils.sendJson(exchange, 200, HttpUtils.ok(service.listMaterials(optionalLong(queryParam(exchange, "projectId")))));
                return true;
            }
            if ("POST".equalsIgnoreCase(method)) {
                authService.requireWritePermission(currentUser, "materials");
                HttpUtils.sendJson(exchange, 201, HttpUtils.ok(service.createMaterial(parseJsonBody(exchange))));
                return true;
            }
        }
        Long id = extractId(path, "/api/materials/");
        if (id == null) {
            return false;
        }
        if ("GET".equalsIgnoreCase(method)) {
            authService.requireReadPermission(currentUser, "materials");
            HttpUtils.sendJson(exchange, 200, HttpUtils.ok(service.getMaterial(id)));
            return true;
        }
        if ("PUT".equalsIgnoreCase(method)) {
            authService.requireWritePermission(currentUser, "materials");
            HttpUtils.sendJson(exchange, 200, HttpUtils.ok(service.updateMaterial(id, parseJsonBody(exchange))));
            return true;
        }
        if ("DELETE".equalsIgnoreCase(method)) {
            authService.requireWritePermission(currentUser, "materials");
            service.deleteMaterial(id);
            HttpUtils.sendJson(exchange, 200, HttpUtils.ok(Map.of("deletedId", id)));
            return true;
        }
        return false;
    }

    private boolean routePayments(HttpExchange exchange, AuthUser currentUser, String method, String path) throws IOException {
        if ("/api/payments".equals(path)) {
            if ("GET".equalsIgnoreCase(method)) {
                authService.requireReadPermission(currentUser, "payments");
                HttpUtils.sendJson(exchange, 200, HttpUtils.ok(service.listPayments(optionalLong(queryParam(exchange, "projectId")))));
                return true;
            }
            if ("POST".equalsIgnoreCase(method)) {
                authService.requireWritePermission(currentUser, "payments");
                HttpUtils.sendJson(exchange, 201, HttpUtils.ok(service.createPayment(parseJsonBody(exchange))));
                return true;
            }
        }
        Long id = extractId(path, "/api/payments/");
        if (id == null) {
            return false;
        }
        if ("GET".equalsIgnoreCase(method)) {
            authService.requireReadPermission(currentUser, "payments");
            HttpUtils.sendJson(exchange, 200, HttpUtils.ok(service.getPayment(id)));
            return true;
        }
        if ("PUT".equalsIgnoreCase(method)) {
            authService.requireWritePermission(currentUser, "payments");
            HttpUtils.sendJson(exchange, 200, HttpUtils.ok(service.updatePayment(id, parseJsonBody(exchange))));
            return true;
        }
        if ("DELETE".equalsIgnoreCase(method)) {
            authService.requireWritePermission(currentUser, "payments");
            service.deletePayment(id);
            HttpUtils.sendJson(exchange, 200, HttpUtils.ok(Map.of("deletedId", id)));
            return true;
        }
        return false;
    }

    private Map<String, Object> parseJsonBody(HttpExchange exchange) throws IOException {
        return JsonUtils.parseObject(HttpUtils.readBody(exchange));
    }

    private void filterOptionsByPermission(Map<String, Object> result, AuthUser currentUser) {
        filterOptionList(result, currentUser, "customers");
        filterOptionList(result, currentUser, "employees");
        filterOptionList(result, currentUser, "projects");
        filterOptionList(result, currentUser, "suppliers");
    }

    private void filterOptionList(Map<String, Object> result, AuthUser currentUser, String resource) {
        if (!authService.canReadPermission(currentUser, resource)) {
            result.put(resource, List.of());
        }
    }

    private String queryParam(HttpExchange exchange, String name) {
        String query = exchange.getRequestURI().getRawQuery();
        if (query == null || query.isBlank()) {
            return "";
        }
        for (String item : query.split("&")) {
            String[] pair = item.split("=", 2);
            if (pair.length == 2 && name.equals(pair[0])) {
                return URLDecoder.decode(pair[1], StandardCharsets.UTF_8);
            }
        }
        return "";
    }

    private Long extractId(String path, String prefix) {
        if (!path.startsWith(prefix) || path.length() <= prefix.length()) {
            return null;
        }
        String remain = path.substring(prefix.length());
        if (remain.contains("/")) {
            return null;
        }
        try {
            return Long.parseLong(remain);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private Long extractId(String path, String prefix, String suffix) {
        if (!path.startsWith(prefix) || !path.endsWith(suffix)) {
            return null;
        }
        String remain = path.substring(prefix.length(), path.length() - suffix.length());
        if (remain.isBlank() || remain.contains("/")) {
            return null;
        }
        try {
            return Long.parseLong(remain);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private Long optionalLong(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            throw new AppException(400, "参数必须为整数。");
        }
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

}
