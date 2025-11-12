package com.group8.evcoownership.enums;

public enum MemberFeedbackStatus {
    PENDING,    // Member DISAGREE - cần admin xử lý
    ACCEPTED,   // Member AGREE - đã đồng ý, không cần xử lý
    APPROVED,   // Admin đã chấp nhận và chỉnh sửa contract dựa trên feedback DISAGREE
    REJECTED    // Admin từ chối, không chỉnh sửa contract (trả về không sửa được)
}

