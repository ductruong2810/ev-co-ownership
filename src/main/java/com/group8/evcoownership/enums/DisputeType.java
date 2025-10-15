package com.group8.evcoownership.enums;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum DisputeType {
    FINANCIAL, USAGE, DECISION, CHARGING, OTHERS;
    @JsonCreator public static DisputeType from(String v){ return v==null?null: valueOf(v.trim().toUpperCase()); }
    @JsonValue public String toValue(){ return name(); }
}
