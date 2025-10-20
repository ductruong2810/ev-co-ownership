package com.group8.evcoownership.enums;

import lombok.Getter;

@Getter
public enum NotificationType {
    // Booking related
    BOOKING_CREATED("BOOKING_CREATED", "Booking Created"),
    BOOKING_CONFLICT("BOOKING_CONFLICT", "Booking Conflict"),
    BOOKING_CANCELLED("BOOKING_CANCELLED", "Booking Cancelled"),
    BOOKING_REMINDER("BOOKING_REMINDER", "Booking Reminder"),

    // Payment related
    PAYMENT_SUCCESS("PAYMENT_SUCCESS", "Payment Successful"),
    PAYMENT_FAILED("PAYMENT_FAILED", "Payment Failed"),
    PAYMENT_REMINDER("PAYMENT_REMINDER", "Payment Reminder"),
    DEPOSIT_REQUIRED("DEPOSIT_REQUIRED", "Deposit Required"),
    DEPOSIT_OVERDUE("DEPOSIT_OVERDUE", "Deposit Overdue"),

    // Contract related
    CONTRACT_CREATED("CONTRACT_CREATED", "Contract Created"),
    CONTRACT_APPROVAL_PENDING("CONTRACT_APPROVAL_PENDING", "Contract Pending Approval"),
    CONTRACT_APPROVED("CONTRACT_APPROVED", "Contract Approved"),
    CONTRACT_REJECTED("CONTRACT_REJECTED", "Contract Rejected"),
    CONTRACT_EXPIRING("CONTRACT_EXPIRING", "Contract Expiring"),

    // Group related
    GROUP_CREATED("GROUP_CREATED", "Group Created"),
    GROUP_INVITATION("GROUP_INVITATION", "Group Invitation"),
    GROUP_MEMBER_JOINED("GROUP_MEMBER_JOINED", "New Member Joined"),
    GROUP_MEMBER_LEFT("GROUP_MEMBER_LEFT", "Member Left Group"),
    GROUP_STATUS_CHANGED("GROUP_STATUS_CHANGED", "Group Status Changed"),

    // Maintenance related
    MAINTENANCE_REQUESTED("MAINTENANCE_REQUESTED", "Maintenance Requested"),
    MAINTENANCE_APPROVED("MAINTENANCE_APPROVED", "Maintenance Approved"),
    MAINTENANCE_COMPLETED("MAINTENANCE_COMPLETED", "Maintenance Completed"),
    MAINTENANCE_OVERDUE("MAINTENANCE_OVERDUE", "Maintenance Overdue"),

    // Vehicle related
    VEHICLE_AVAILABLE("VEHICLE_AVAILABLE", "Vehicle Available"),
    VEHICLE_UNAVAILABLE("VEHICLE_UNAVAILABLE", "Vehicle Unavailable"),
    VEHICLE_EMERGENCY("VEHICLE_EMERGENCY", "Vehicle Emergency"),

    // Fund related
    FUND_LOW_BALANCE("FUND_LOW_BALANCE", "Low Fund Balance"),
    FUND_CONTRIBUTION_REQUIRED("FUND_CONTRIBUTION_REQUIRED", "Fund Contribution Required"),
    FUND_EXPENSE_APPROVED("FUND_EXPENSE_APPROVED", "Fund Expense Approved"),

    // System related
    SYSTEM_MAINTENANCE("SYSTEM_MAINTENANCE", "System Maintenance"),
    SECURITY_ALERT("SECURITY_ALERT", "Security Alert"),
    POLICY_UPDATE("POLICY_UPDATE", "Policy Update");

    private final String code;
    private final String description;

    NotificationType(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public static NotificationType fromCode(String code) {
        for (NotificationType type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown notification type code: " + code);
    }
}
