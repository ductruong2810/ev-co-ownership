package com.group8.evcoownership.service;

import com.group8.evcoownership.entity.OwnershipGroup;
import com.group8.evcoownership.entity.Vehicle;
import com.group8.evcoownership.repository.VehicleRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class DepositCalculationService {

    private final VehicleRepository vehicleRepository;

    public DepositCalculationService(VehicleRepository vehicleRepository) {
        this.vehicleRepository = vehicleRepository;
    }

    // Base deposit amount (có thể config từ application.properties)
    private static final BigDecimal BASE_DEPOSIT_AMOUNT = new BigDecimal("2000000"); // 2 triệu VND

    // Multiplier based on member capacity
    private static final BigDecimal CAPACITY_MULTIPLIER = new BigDecimal("0.1"); // 10% per member

    /**
     * Tính toán required deposit amount dựa trên member capacity
     * <p>
     * Công thức: BASE_DEPOSIT_AMOUNT + (memberCapacity * CAPACITY_MULTIPLIER * BASE_DEPOSIT_AMOUNT)
     * <p>
     * Ví dụ:
     * - 2 members: 2,000,000 + (2 * 0.1 * 2,000,000) = 2,400,000 VND
     * - 4 members: 2,000,000 + (4 * 0.1 * 2,000,000) = 2,800,000 VND
     * - 6 members: 2,000,000 + (6 * 0.1 * 2,000,000) = 3,200,000 VND
     */
    public BigDecimal calculateRequiredDepositAmount(Integer memberCapacity) {
        if (memberCapacity == null || memberCapacity <= 0) {
            return BASE_DEPOSIT_AMOUNT;
        }
        BigDecimal capacityFactor = BigDecimal.valueOf(memberCapacity).multiply(CAPACITY_MULTIPLIER);
        BigDecimal additionalAmount = BASE_DEPOSIT_AMOUNT.multiply(capacityFactor);

        return BASE_DEPOSIT_AMOUNT.add(additionalAmount);
    }

    /**
     * Tính toán required deposit amount dựa trên OwnershipGroup
     * <p>
     * Công thức ưu tiên:
     * 1. Nếu có Vehicle với vehicleValue: vehicleValue * 10% (tổng deposit cho cả group)
     * 2. Nếu không có Vehicle hoặc vehicleValue: BASE_DEPOSIT_AMOUNT + (memberCapacity * CAPACITY_MULTIPLIER * BASE_DEPOSIT_AMOUNT)
     *
     * @param group OwnershipGroup để tính deposit
     * @return Required deposit amount (tổng cho cả group)
     */
    public BigDecimal calculateRequiredDepositAmount(OwnershipGroup group) {
        if (group == null) {
            return BASE_DEPOSIT_AMOUNT;
        }

        // Tìm Vehicle của group - dùng groupId để tránh lazy loading issues
        Vehicle vehicle = vehicleRepository.findByOwnershipGroup_GroupId(group.getGroupId()).orElse(null);

        if (vehicle != null && vehicle.getVehicleValue() != null && vehicle.getVehicleValue().compareTo(BigDecimal.ZERO) > 0) {
            // Có vehicleValue, tính tổng deposit = 10% giá trị xe
            return vehicle.getVehicleValue().multiply(new BigDecimal("0.1"));
        } else {
            // Không có vehicleValue, tính theo memberCapacity
            return calculateRequiredDepositAmount(group.getMemberCapacity());
        }
    }

    /**
     * Tính toán required deposit amount dựa trên giá trị xe và tỷ lệ sở hữu
     * Công thức: vehicleValue * 10% * ownershipPercentage / 100
     * Ví dụ với vehicleValue = 1,000,000,000 VND (1 tỷ):
     * - Thành viên sở hữu 40%: 1,000,000,000 * 0.1 * 0.4 = 40,000,000 VND
     * - Thành viên sở hữu 35%: 1,000,000,000 * 0.1 * 0.35 = 35,000,000 VND
     * - Thành viên sở hữu 25%: 1,000,000,000 * 0.1 * 0.25 = 25,000,000 VND
     */
    public BigDecimal calculateRequiredDepositAmount(BigDecimal vehicleValue, BigDecimal ownershipPercentage) {
        if (vehicleValue == null || vehicleValue.compareTo(BigDecimal.ZERO) <= 0) {
            return BASE_DEPOSIT_AMOUNT;
        }

        if (ownershipPercentage == null || ownershipPercentage.compareTo(BigDecimal.ZERO) <= 0) {
            return BASE_DEPOSIT_AMOUNT;
        }

        // Tính deposit theo tỷ lệ sở hữu: vehicleValue * 10% * ownershipPercentage / 100
        BigDecimal depositAmount = vehicleValue
                .multiply(new BigDecimal("0.1")) // 10% giá trị xe
                .multiply(ownershipPercentage)   // Nhân với tỷ lệ sở hữu (%)
                .divide(new BigDecimal("100"), 0, RoundingMode.UP); // Chia 100 để chuyển % thành số thập phân

        // Đảm bảo deposit tối thiểu là BASE_DEPOSIT_AMOUNT
        return depositAmount.max(BASE_DEPOSIT_AMOUNT);
    }

    /**
     * Tính toán required deposit amount dựa trên giá trị xe và member capacity (legacy method)
     * <p>
     * Công thức: (vehicleValue * 10%) / memberCapacity
     * <p>
     * Ví dụ với vehicleValue = 1,000,000,000 VND (1 tỷ):
     * - 2 members: (1,000,000,000 * 0.1) / 2 = 50,000,000 VND
     * - 4 members: (1,000,000,000 * 0.1) / 4 = 25,000,000 VND
     * - 6 members: (1,000,000,000 * 0.1) / 6 = 16,666,667 VND
     */
    public BigDecimal calculateRequiredDepositAmount(BigDecimal vehicleValue, Integer memberCapacity) {
        if (vehicleValue == null || vehicleValue.compareTo(BigDecimal.ZERO) <= 0) {
            return calculateRequiredDepositAmount(memberCapacity);
        }

        if (memberCapacity == null || memberCapacity <= 0) {
            return BASE_DEPOSIT_AMOUNT;
        }

        // Nếu có giá trị xe, tính deposit = 10% giá trị xe / số thành viên
        BigDecimal depositPerMember = vehicleValue.multiply(new BigDecimal("0.1")).divide(
                BigDecimal.valueOf(memberCapacity), 2, RoundingMode.UP);

        // Đảm bảo deposit tối thiểu là BASE_DEPOSIT_AMOUNT
        BigDecimal minDeposit = calculateRequiredDepositAmount(memberCapacity);

        return depositPerMember.max(minDeposit);
    }

    /**
     * Lấy base deposit amount (để hiển thị cho user)
     */
    public BigDecimal getBaseDepositAmount() {
        return BASE_DEPOSIT_AMOUNT;
    }
}
