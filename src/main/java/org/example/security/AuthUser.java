package org.example.security;

import java.util.List;

public record AuthUser(
        long id,
        String username,
        String displayName,
        String role,
        List<String> permissions
) {
}
