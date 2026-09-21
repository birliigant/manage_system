package org.example.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

public record Project(
        long id,
        String name,
        long customerId,
        String customerName,
        long managerId,
        String managerName,
        String status,
        String style,
        String address,
        double area,
        BigDecimal contractAmount,
        LocalDate startDate,
        LocalDate expectedEndDate,
        String notes
) {
}
