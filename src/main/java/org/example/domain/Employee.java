package org.example.domain;

import java.time.LocalDate;

public record Employee(
        long id,
        String name,
        String role,
        String phone,
        String specialty,
        String status,
        LocalDate hireDate,
        String notes
) {
}
