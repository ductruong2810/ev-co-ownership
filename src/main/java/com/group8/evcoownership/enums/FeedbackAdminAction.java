package com.group8.evcoownership.enums;

public enum FeedbackAdminAction {
    APPROVE,           // Admin đã chỉnh sửa contract để chấp nhận feedback
    REJECT,            // Admin từ chối feedback
    CONTRACT_UPDATED   // Contract terms đã được update - feedback cần review lại
}

