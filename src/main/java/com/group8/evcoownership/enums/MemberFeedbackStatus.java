package com.group8.evcoownership.enums;

public enum MemberFeedbackStatus {
    PENDING,    // Member mới nộp feedback (chưa được admin xử lý)
    APPROVED,   // Admin đã chấp nhận và chỉnh sửa contract dựa trên feedback
    REJECTED    // Admin từ chối, không chỉnh sửa contract (trả về không sửa được)
}

