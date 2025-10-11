package com.group8.evcoownership.enums;

public enum ReportType {
    USER_CHECKIN,           // User xem xe trước khi dùng
    USER_CHECKOUT,          // User báo cáo sau khi dùng
    TECHNICIAN_VERIFICATION, // Technician xác nhận
    TECHNICIAN_CHECKOUT,    // Technician check-out
    REJECTION_RESOLUTION    // Technician giải quyết từ chối
}
