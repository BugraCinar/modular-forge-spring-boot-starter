package dev.modulithforge.audit.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthErrorStatisticsResponse {
    private long totalErrors;
    private long unauthorized401Count;
    private long forbidden403Count;
    private long notFound404Count;
    private long badRequest400Count;
    private long internalServerError500Count;
    private long invalidTokenCount;
    private long accessDeniedCount;
    private Map<String, Long> errorsByType;
    private Map<String, Long> dailyStatistics;
}
