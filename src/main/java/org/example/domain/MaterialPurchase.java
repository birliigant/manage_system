package org.example.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

public record MaterialPurchase(
        long id,
        long projectId,
        String projectName,
        String materialName,
        String category,
        String supplier,
        BigDecimal amount,
        String status,
        LocalDate purchaseDate,
        String notes
) {
}
