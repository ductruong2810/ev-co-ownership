package com.group8.evcoownership.enums;

import lombok.Getter;

/**
 * Enum định nghĩa các loại xe được hỗ trợ trong hệ thống
 */
@Getter
public enum VehicleType {
    CAR("CAR"),
    MOTORCYCLE("MOTORCYCLE");

    private final String value;

    VehicleType(String value) {
        this.value = value;
    }

    /**
     * Tìm VehicleType từ string value
     */
    public static VehicleType fromValue(String value) {
        if (value == null) {
            return null;
        }

        for (VehicleType type : VehicleType.values()) {
            if (type.value.equalsIgnoreCase(value)) {
                return type;
            }
        }

        throw new IllegalArgumentException("Invalid VehicleType: " + value);
    }

    /**
     * Kiểm tra xem có phải xe máy không
     */
    public boolean isMotorcycle() {
        return this == MOTORCYCLE;
    }

    /**
     * Kiểm tra xem có phải xe ô tô không
     */
    public boolean isCar() {
        return this == CAR;
    }
}
