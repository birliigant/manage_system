package org.example.domain;

import java.time.LocalDate;

public record ProjectStage(
        long id,
        long projectId,
        String projectName,
        String stageName,
        String owner,
        String status,
        LocalDate plannedDate,
        LocalDate actualDate,
        String notes
) {
}
