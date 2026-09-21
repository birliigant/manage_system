package org.example.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PaymentRecord(
        long id,
        long projectId,
        String projectName,
        String type,
        BigDecimal amount,
        String status,
        LocalDate paymentDate,
        String payer,
        String notes
) {
}
