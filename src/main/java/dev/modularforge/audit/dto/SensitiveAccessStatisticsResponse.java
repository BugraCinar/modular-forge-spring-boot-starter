package dev.modularforge.audit.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SensitiveAccessStatisticsResponse {
    private long totalAccessLogs;
    private long criticalCount;
    private long highCount;
    private long mediumCount;
    private long lowCount;
    private long emailAlertsSent;
    private Map<String, Long> accessBySeverity;
    private Map<String, Long> accessByCategory;
    private Map<String, Long> dailyStatistics;
}
