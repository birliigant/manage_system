package org.example.domain;

import java.time.LocalDate;

public record Customer(
        long id,
        String name,
        String phone,
        String source,
        String level,
        String intention,
        String address,
        String notes,
        LocalDate createdDate
) {
}
