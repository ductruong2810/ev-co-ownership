package com.group8.evcoownership.enums;

public enum ReportType {
    UserCheckin,           // User xem xe trước khi dùng
    UserCheckout,          // User báo cáo sau khi dùng
    TechnicianVerification, // Technician xác nhận
    TechnicianCheckout,    // Technician check-out
    RejectionResolution    // Technician giải quyết từ chối
}
