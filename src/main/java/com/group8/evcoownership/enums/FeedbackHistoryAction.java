package com.group8.evcoownership.enums;

/**
 * Represents the type of action that produced a feedback history snapshot.
 */
public enum FeedbackHistoryAction {
    MEMBER_SUBMIT,
    MEMBER_RESUBMIT,
    MEMBER_AGREE,
    MEMBER_WITHDRAW,
    ADMIN_APPROVE,
    ADMIN_REJECT,
    CONTRACT_INVALIDATED,
    CONTRACT_UPDATED
}

