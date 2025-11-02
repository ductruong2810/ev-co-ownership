package com.group8.evcoownership.enums;

public enum RejectionCategory {
    INSUFFICIENT_EVIDENCE,   // Thiếu bằng chứng rõ ràng
    OUT_OF_SCOPE,             // Không thuộc phạm vi bảo hành / chia sẻ
    DUPLICATE_REPORT,         // Báo cáo trùng lặp
    FAKE_REPORT,              // Báo cáo sai / gian dối
    UNAUTHORIZED_ACTION,      // Tự ý sửa chữa, không thông qua hệ thống
    COST_DISCREPANCY,         // Chi phí khai báo không hợp lý
    OTHER                     // Lý do khác
}