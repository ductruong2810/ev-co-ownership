package com.group8.evcoownership.enums;

public enum MemberFeedbackStatus {
    PENDING,    // Member DISAGREE - cần admin xử lý
    APPROVED,   // Member AGREE hoặc Admin đã approve feedback DISAGREE - đã được approve
    REJECTED    // Admin đã reject feedback DISAGREE - workflow kết thúc
}

