package com.group8.evcoownership.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum RelatedEntityType {
    PAYMENT, EXPENSE, BOOKING, INCIDENT, VEHICLE_REPORT;

    @JsonCreator
    public static RelatedEntityType from(String v) {
        return v == null ? null : valueOf(v.trim().toUpperCase());
    }

    @JsonValue
    public String toValue() {
        return name();
    }
}