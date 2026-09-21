package org.example.security;

import com.sun.net.httpserver.HttpExchange;
import org.example.db.DatabaseManager;
import org.example.util.AppException;
import org.example.util.HttpUtils;
import org.example.util.PasswordUtils;

import java.io.IOException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class AuthService {
    private static final Map<String, Set<String>> READ_ROLES = Map.of(
            "dashboard", Set.of("ADMIN", "MANAGER", "FINANCE", "DESIGNER"),
            "customers", Set.of("ADMIN", "MANAGER", "DESIGNER"),
            "employees", Set.of("ADMIN", "MANAGER"),
            "users", Set.of("ADMIN"),
            "projects", Set.of("ADMIN", "MANAGER", "FINANCE", "DESIGNER"),
            "stages", Set.of("ADMIN", "MANAGER", "DESIGNER"),
            "materials", Set.of("ADMIN", "MANAGER"),
            "suppliers", Set.of("ADMIN", "MANAGER"),
            "payments", Set.of("ADMIN", "MANAGER", "FINANCE")
    );

    private static final Map<String, Set<String>> WRITE_ROLES = Map.of(
            "customers", Set.of("ADMIN", "MANAGER", "DESIGNER"),
            "employees", Set.of("ADMIN"),
            "users", Set.of("ADMIN"),
            "projects", Set.of("ADMIN", "MANAGER", "DESIGNER"),
            "stages", Set.of("ADMIN", "MANAGER", "DESIGNER"),
            "materials", Set.of("ADMIN", "MANAGER"),
            "suppliers", Set.of("ADMIN", "MANAGER"),
            "payments", Set.of("ADMIN", "FINANCE")
    );

    private final DatabaseManager databaseManager;

    public AuthService(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    public Map<String, Object> login(String username, String password) {
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            throw new AppException(400, "用户名和密码不能为空。");
        }

        UserRow user = databaseManager.queryOne(
                "select id, username, display_name, password_hash, password_salt, role, status from users where username = ?",
                statement -> statement.setString(1, username.trim()),
                resultSet -> new UserRow(
                        resultSet.getLong("id"),
                        resultSet.getString("username"),
                        resultSet.getString("display_name"),
                        resultSet.getString("password_hash"),
                        resultSet.getString("password_salt"),
                        resultSet.getString("role"),
                        resultSet.getString("status")
                )
        );

        if (user == null || !"启用".equals(user.status())) {
            throw new AppException(401, "用户名或密码错误。");
        }
        if (!PasswordUtils.verifyPassword(password, user.passwordSalt(), user.passwordHash())) {
            throw new AppException(401, "用户名或密码错误。");
        }

        clearExpiredSessions();
        String token = generateToken();
        LocalDateTime expiresAt = LocalDateTime.now().plusHours(12);
        databaseManager.insert(
                "insert into user_sessions(user_id, token, expires_at, created_at) values (?, ?, ?, ?)",
                statement -> {
                    statement.setLong(1, user.id());
                    statement.setString(2, token);
                    statement.setTimestamp(3, Timestamp.valueOf(expiresAt));
                    statement.setTimestamp(4, Timestamp.valueOf(LocalDateTime.now()));
                });

        AuthUser authUser = toAuthUser(user.id(), user.username(), user.displayName(), user.role());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("token", token);
        result.put("expiresAt", expiresAt.toString());
        result.put("user", toMap(authUser));
        return result;
    }

    public Map<String, Object> currentUser(HttpExchange exchange) {
        return toMap(requireUser(exchange));
    }

    public void logout(HttpExchange exchange) {
        String token = readBearerToken(exchange);
        if (token == null) {
            throw new AppException(401, "未登录或登录已失效。");
        }
        databaseManager.update("delete from user_sessions where token = ?", statement -> statement.setString(1, token));
    }

    public AuthUser requireUser(HttpExchange exchange) {
        String token = readBearerToken(exchange);
        if (token == null) {
            throw new AppException(401, "未登录或登录已失效。");
        }

        clearExpiredSessions();
        AuthUser authUser = databaseManager.queryOne(
                """
                select u.id, u.username, u.display_name, u.role
                from user_sessions s
                join users u on u.id = s.user_id
                where s.token = ? and s.expires_at > ?
                """,
                statement -> {
                    statement.setString(1, token);
                    statement.setTimestamp(2, Timestamp.valueOf(LocalDateTime.now()));
                },
                resultSet -> toAuthUser(
                        resultSet.getLong("id"),
                        resultSet.getString("username"),
                        resultSet.getString("display_name"),
                        resultSet.getString("role")
                )
        );
        if (authUser == null) {
            throw new AppException(401, "未登录或登录已失效。");
        }
        return authUser;
    }

    public void requireWritePermission(AuthUser user, String resource) {
        Set<String> roles = WRITE_ROLES.get(resource);
        if (roles == null || !roles.contains(user.role())) {
            throw new AppException(403, "当前账号无权执行此操作。");
        }
    }

    public void requireReadPermission(AuthUser user, String resource) {
        Set<String> roles = READ_ROLES.get(resource);
        if (roles == null || !roles.contains(user.role())) {
            throw new AppException(403, "当前账号无权查看此模块。");
        }
    }

    public boolean canReadPermission(AuthUser user, String resource) {
        Set<String> roles = READ_ROLES.get(resource);
        return roles != null && roles.contains(user.role());
    }

    public Map<String, Object> permissionOverview(AuthUser user) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("role", user.role());
        result.put("permissions", user.permissions());
        return result;
    }

    private List<String> permissionsFor(String role) {
        List<String> permissions = new ArrayList<>();
        permissions.add("options:read");

        for (Map.Entry<String, Set<String>> entry : READ_ROLES.entrySet()) {
            if (entry.getValue().contains(role)) {
                permissions.add(entry.getKey() + ":read");
            }
        }

        for (Map.Entry<String, Set<String>> entry : WRITE_ROLES.entrySet()) {
            if (entry.getValue().contains(role)) {
                permissions.add(entry.getKey() + ":write");
                permissions.add(entry.getKey() + ":delete");
            }
        }
        return permissions;
    }

    private AuthUser toAuthUser(long id, String username, String displayName, String role) {
        return new AuthUser(id, username, displayName, role, permissionsFor(role));
    }

    private Map<String, Object> toMap(AuthUser user) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", user.id());
        result.put("username", user.username());
        result.put("displayName", user.displayName());
        result.put("role", user.role());
        result.put("permissions", user.permissions());
        return result;
    }

    private void clearExpiredSessions() {
        databaseManager.update(
                "delete from user_sessions where expires_at <= ?",
                statement -> statement.setTimestamp(1, Timestamp.valueOf(LocalDateTime.now()))
        );
    }

    private String readBearerToken(HttpExchange exchange) {
        String authorization = exchange.getRequestHeaders().getFirst("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return null;
        }
        return authorization.substring("Bearer ".length()).trim();
    }

    private String generateToken() {
        byte[] bytes = PasswordUtils.randomBytes(32);
        return HexFormat.of().formatHex(bytes);
    }

    private record UserRow(
            long id,
            String username,
            String displayName,
            String passwordHash,
            String passwordSalt,
            String role,
            String status
    ) {
    }
}
