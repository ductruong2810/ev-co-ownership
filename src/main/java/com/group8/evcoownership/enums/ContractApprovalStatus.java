package com.group8.evcoownership.enums;

public enum ContractApprovalStatus {
    PENDING,                    // chờ ký
    PENDING_MEMBER_APPROVAL,    // đã ký bởi admin group, chờ các member approve/reject
    SIGNED,                     // tất cả members đã approve, chờ system admin duyệt
    APPROVED,                   // đã duyệt bởi system admin
    REJECTED                    // bị từ chối
}
