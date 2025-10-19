package com.group8.evcoownership.service;

import com.group8.evcoownership.entity.OwnershipGroup;
import com.group8.evcoownership.entity.Vehicle;
import com.group8.evcoownership.repository.VehicleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DepositCalculationServiceTest {

    @Mock
    private VehicleRepository vehicleRepository;

    @InjectMocks
    private DepositCalculationService depositCalculationService;

    private OwnershipGroup testGroup;
    private Vehicle testVehicle;

    @BeforeEach
    void setUp() {
        testGroup = OwnershipGroup.builder()
                .groupId(1L)
                .groupName("Test EV Group")
                .memberCapacity(3)
                .build();

        testVehicle = Vehicle.builder()
                .Id(1L)
                .brand("VinFast")
                .model("VF 8 Plus")
                .vehicleValue(new BigDecimal("950000000")) // 950 triệu VND
                .build();
    }

    @Test
    void testCalculateRequiredDepositAmount_ByMemberCapacity() {
        // Test với 3 thành viên
        // Expected: 2,000,000 + (3 * 0.1 * 2,000,000) = 2,600,000 VND
        BigDecimal expected = new BigDecimal("2600000");

        BigDecimal result = depositCalculationService.calculateRequiredDepositAmount(Integer.valueOf(3));

        assertEquals(0, expected.compareTo(result));
    }

    @Test
    void testCalculateRequiredDepositAmount_ByMemberCapacity_EdgeCases() {
        // Test với 0 thành viên (fallback to base amount)
        BigDecimal resultZero = depositCalculationService.calculateRequiredDepositAmount(Integer.valueOf(0));
        assertEquals(0, new BigDecimal("2000000").compareTo(resultZero));

        // Test với null (fallback to base amount)
        BigDecimal resultNull = depositCalculationService.calculateRequiredDepositAmount((Integer) null);
        assertEquals(0, new BigDecimal("2000000").compareTo(resultNull));

        // Test với 1 thành viên
        BigDecimal resultOne = depositCalculationService.calculateRequiredDepositAmount(Integer.valueOf(1));
        assertEquals(0, new BigDecimal("2200000").compareTo(resultOne)); // 2M + (1 * 0.1 * 2M) = 2.2M
    }

    @Test
    void testCalculateRequiredDepositAmount_ByOwnershipPercentage() {
        // Test với xe giá 950 triệu VND và tỷ lệ sở hữu 40%
        // Expected: 950,000,000 * 0.1 * 0.4 = 38,000,000 VND
        BigDecimal vehicleValue = new BigDecimal("950000000");
        BigDecimal ownershipPercentage = new BigDecimal("40.00");
        BigDecimal expected = new BigDecimal("38000000");

        BigDecimal result = depositCalculationService.calculateRequiredDepositAmount(
                vehicleValue, (BigDecimal) ownershipPercentage);

        assertEquals(0, expected.compareTo(result));
    }

    @Test
    void testCalculateRequiredDepositAmount_ByOwnershipPercentage_EdgeCases() {
        // Test với vehicleValue = 0 (fallback to base amount)
        BigDecimal resultZero = depositCalculationService.calculateRequiredDepositAmount(
                BigDecimal.ZERO, (BigDecimal) new BigDecimal("40.00"));
        assertEquals(0, new BigDecimal("2000000").compareTo(resultZero));

        // Test với ownershipPercentage = 0 (fallback to base amount)
        BigDecimal resultZeroPercent = depositCalculationService.calculateRequiredDepositAmount(
                new BigDecimal("950000000"), (BigDecimal) BigDecimal.ZERO);
        assertEquals(0, new BigDecimal("2000000").compareTo(resultZeroPercent));

        // Test với null values (fallback to base amount)
        BigDecimal resultNull = depositCalculationService.calculateRequiredDepositAmount((BigDecimal) null, (BigDecimal) null);
        assertEquals(0, new BigDecimal("2000000").compareTo(resultNull));
    }

    @Test
    void testCalculateRequiredDepositAmount_ByOwnershipPercentage_MinimumDeposit() {
        // Test với tỷ lệ sở hữu rất nhỏ (đảm bảo minimum deposit)
        BigDecimal vehicleValue = new BigDecimal("10000000"); // 10 triệu VND
        BigDecimal ownershipPercentage = new BigDecimal("1.00"); // 1%
        // Calculated: 10,000,000 * 0.1 * 0.01 = 100,000 VND
        // But minimum is 2,000,000 VND
        BigDecimal expected = new BigDecimal("2000000");

        BigDecimal result = depositCalculationService.calculateRequiredDepositAmount(
                vehicleValue, (BigDecimal) ownershipPercentage);

        assertEquals(0, expected.compareTo(result));
    }

    @Test
    void testCalculateRequiredDepositAmount_ByOwnershipGroup_WithVehicle() {
        // Arrange
        testGroup.setMemberCapacity(3);
        when(vehicleRepository.findByOwnershipGroup(testGroup)).thenReturn(Optional.of(testVehicle));

        // Act
        BigDecimal result = depositCalculationService.calculateRequiredDepositAmount(testGroup);

        // Assert
        // Should use vehicle value calculation: 950M * 0.1 = 95M, but we need member capacity
        // Since we have vehicle, it should use vehicle value method
        assertNotNull(result);
        assertTrue(result.compareTo(new BigDecimal("2000000")) >= 0); // At least minimum

        verify(vehicleRepository).findByOwnershipGroup(testGroup);
    }

    @Test
    void testCalculateRequiredDepositAmount_ByOwnershipGroup_WithoutVehicle() {
        // Arrange
        testGroup.setMemberCapacity(3);
        when(vehicleRepository.findByOwnershipGroup(testGroup)).thenReturn(Optional.empty());

        // Act
        BigDecimal result = depositCalculationService.calculateRequiredDepositAmount(testGroup);

        // Assert
        // Should use member capacity calculation: 2M + (3 * 0.1 * 2M) = 2.6M
        BigDecimal expected = new BigDecimal("2600000");
        assertEquals(0, expected.compareTo(result));

        verify(vehicleRepository).findByOwnershipGroup(testGroup);
    }

    @Test
    void testCalculateRequiredDepositAmount_ByOwnershipGroup_NullGroup() {
        // Act
        BigDecimal result = depositCalculationService.calculateRequiredDepositAmount((OwnershipGroup) null);

        // Assert
        assertEquals(new BigDecimal("2000000"), result);
        verify(vehicleRepository, never()).findByOwnershipGroup(any());
    }

    @Test
    void testCalculateRequiredDepositAmount_ByVehicleValueAndCapacity() {
        // Test legacy method với xe giá 950 triệu và 3 thành viên
        // Expected: (950,000,000 * 0.1) / 3 = 31,666,666.67 VND
        BigDecimal vehicleValue = new BigDecimal("950000000");
        Integer memberCapacity = 3;
        BigDecimal expected = new BigDecimal("31666666.67"); // Actual result from calculation

        BigDecimal result = depositCalculationService.calculateRequiredDepositAmount(vehicleValue, memberCapacity);

        assertEquals(0, expected.compareTo(result));
    }

    @Test
    void testCalculateRequiredDepositAmount_ByVehicleValueAndCapacity_EdgeCases() {
        // Test với vehicleValue = 0
        BigDecimal resultZero = depositCalculationService.calculateRequiredDepositAmount(
                BigDecimal.ZERO, 3);
        assertEquals(0, new BigDecimal("2600000").compareTo(resultZero)); // Fallback to capacity calculation

        // Test với memberCapacity = 0
        BigDecimal resultZeroCapacity = depositCalculationService.calculateRequiredDepositAmount(
                new BigDecimal("950000000"), 0);
        assertEquals(0, new BigDecimal("2000000").compareTo(resultZeroCapacity)); // Fallback to base amount

        // Test với null values
        BigDecimal resultNull = depositCalculationService.calculateRequiredDepositAmount((BigDecimal) null, (Integer) null);
        assertEquals(0, new BigDecimal("2000000").compareTo(resultNull));
    }

    @Test
    void testGetBaseDepositAmount() {
        BigDecimal baseAmount = depositCalculationService.getBaseDepositAmount();
        assertEquals(0, new BigDecimal("2000000").compareTo(baseAmount));
    }

    @Test
    void testDepositCalculationExamples() {
        // Test các ví dụ thực tế

        // Ví dụ 1: Xe VinFast VF 8 Plus (950 triệu), 3 thành viên với tỷ lệ 40%, 35%, 25%
        BigDecimal vehicleValue = new BigDecimal("950000000");

        // Member 1: 40% ownership
        BigDecimal deposit1 = depositCalculationService.calculateRequiredDepositAmount(
                vehicleValue, new BigDecimal("40.00"));
        assertEquals(0, new BigDecimal("38000000").compareTo(deposit1)); // 38 triệu VND

        // Member 2: 35% ownership
        BigDecimal deposit2 = depositCalculationService.calculateRequiredDepositAmount(
                vehicleValue, new BigDecimal("35.00"));
        assertEquals(0, new BigDecimal("33250000").compareTo(deposit2)); // 33.25 triệu VND

        // Member 3: 25% ownership
        BigDecimal deposit3 = depositCalculationService.calculateRequiredDepositAmount(
                vehicleValue, new BigDecimal("25.00"));
        assertEquals(0, new BigDecimal("23750000").compareTo(deposit3)); // 23.75 triệu VND

        // Tổng: 38M + 33.25M + 23.75M = 95M VND (10% giá trị xe)
        BigDecimal totalDeposit = deposit1.add(deposit2).add(deposit3);
        assertEquals(0, new BigDecimal("95000000").compareTo(totalDeposit));
    }

    @Test
    void testDepositCalculationRounding() {
        // Test rounding behavior với số lẻ
        BigDecimal vehicleValue = new BigDecimal("100000000"); // 100 triệu VND
        BigDecimal ownershipPercentage = new BigDecimal("33.33"); // 33.33%

        // Expected: 100,000,000 * 0.1 * 0.3333 = 3,333,000 VND
        // Should round up to 2 decimal places
        BigDecimal result = depositCalculationService.calculateRequiredDepositAmount(vehicleValue, ownershipPercentage);

        // Should be rounded up due to RoundingMode.UP
        assertTrue(result.compareTo(new BigDecimal("3333000")) >= 0);
        assertTrue(result.compareTo(new BigDecimal("3334000")) <= 0);
    }
}
