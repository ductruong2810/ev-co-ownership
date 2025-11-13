package com.group8.evcoownership.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.group8.evcoownership.enums.ContractApprovalStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ContractGenerationResponseDTO {
    private ContractSection contract;
    private GroupSection group;
    private VehicleSection vehicle;
    private FinanceSection finance;
    private MaintenanceSection maintenance;
    private DisputeSection dispute;
    private List<OwnerInfo> owners;
    private Long groupId;
    private Long userId;
    private String terms;
    private LocalDate startDate;
    private LocalDate endDate;
    private String contractNumber;
    private LocalDateTime generatedAt;
    private Long contractId;
    private ContractApprovalStatus status;
    private Boolean isActive;
    private Boolean savedToDatabase;
    private LocalDateTime signedAt;
    private Boolean signed;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ContractSection {
        private String number;
        private String effectiveDate;
        private String endDate;
        private String termLabel;
        private ContractApprovalStatus status;
        private String location;
        private String signDate;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class GroupSection {
        private String name;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class VehicleSection {
        private String model;
        private String plate;
        private String vin;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class FinanceSection {
        private BigDecimal vehiclePrice;
        private BigDecimal depositAmount;
        private String contributionRule;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class MaintenanceSection {
        private String approval;
        private String insurance;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class DisputeSection {
        private String voting;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class OwnerInfo {
        private Long userId;
        private String name;
        private String phone;
        private String email;
        private String idNo;
        private BigDecimal share;
        private String userRole;
    }
}

