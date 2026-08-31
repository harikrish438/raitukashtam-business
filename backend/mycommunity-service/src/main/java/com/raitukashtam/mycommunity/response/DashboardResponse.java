package com.raitukashtam.mycommunity.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DashboardResponse {
    private Long communityId;
    private String communityName;
    private Integer totalUnits;
    private long occupiedUnits;
    private long vacantUnits;
    private BigDecimal pendingDuesTotal;
    private BigDecimal maintenanceCollectedThisMonth;
    private BigDecimal expensesThisMonth;
    private BigDecimal expensesLastMonth;
    private BigDecimal communityBalance;
    private List<AnnouncementResponse> recentAnnouncements;
    private List<ActivityItemResponse> recentActivity;
}
