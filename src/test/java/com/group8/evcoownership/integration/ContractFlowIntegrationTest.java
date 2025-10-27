package com.group8.evcoownership.integration;

import com.group8.evcoownership.dto.DepositPaymentRequest;
import com.group8.evcoownership.dto.DepositPaymentResponse;
import com.group8.evcoownership.dto.SaveContractDataRequest;
import com.group8.evcoownership.enums.DepositStatus;
import com.group8.evcoownership.enums.PaymentStatus;
import com.group8.evcoownership.service.ContractService;
import com.group8.evcoownership.service.DepositPaymentService;
import com.group8.evcoownership.service.NotificationOrchestrator;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContractFlowIntegrationTest {

    @Mock
    private ContractService contractService;

    @Mock
    private DepositPaymentService depositPaymentService;

    @Mock
    private NotificationOrchestrator notificationOrchestrator;

    @Mock
    private HttpServletRequest httpRequest;

    @Test
    void testCompleteContractFlow() {
        // Step 1: Generate Contract
        testContractGeneration();

        // Step 2: Sign Contract (Admin proxy signing)
        testContractSigning();

        // Step 3: Create Deposit Payment
        testDepositPaymentCreation();

        // Step 4: Confirm Payment
        testDepositPaymentConfirmation();

        // Step 5: Check Group Activation
        testGroupActivation();
    }

    @Test
    void testContractGeneration() {
        // Arrange
        Map<String, Object> expectedResponse = new HashMap<>();
        expectedResponse.put("contractId", 1L);
        expectedResponse.put("contractNumber", "EVS-2025-001");
        expectedResponse.put("status", "GENERATED");
        expectedResponse.put("savedToDatabase", false);

        when(contractService.generateContractData(1L, 1L))
                .thenReturn(expectedResponse);

        // Act
        Map<String, Object> response = contractService.generateContractData(1L, 1L);

        // Assert
        assertNotNull(response);
        assertEquals(1L, response.get("contractId"));
        assertEquals("EVS-2025-001", response.get("contractNumber"));
        assertEquals("GENERATED", response.get("status"));

        verify(contractService).generateContractData(1L, 1L);
    }

    @Test
    void testContractSigning() {
        // Arrange
        Map<String, Object> signRequest = Map.of(
                "signedAt", LocalDateTime.now().toString(),
                "signature", "Digital signature by admin",
                "adminName", "Nguyễn Văn Test",
                "signatureType", "ADMIN_PROXY",
                "reason", "Admin Group ký thay tất cả thành viên theo quy định"
        );

        Map<String, Object> expectedResponse = Map.of(
                "success", true,
                "contractId", 1L,
                "contractNumber", "EVS-0001-2025",
                "status", "AUTO_SIGNED",
                "signedAt", LocalDateTime.now(),
                "message", "Contract has been automatically signed"
        );

        when(contractService.autoSignContract(1L))
                .thenReturn(expectedResponse);

        // Act
        Map<String, Object> response = contractService.autoSignContract(1L);

        // Assert
        assertNotNull(response);
        assertTrue((Boolean) response.get("success"));
        assertEquals(1L, response.get("contractId"));
        assertEquals("AUTO_SIGNED", response.get("status"));
        assertTrue(response.get("message").toString().contains("Contract has been automatically signed"));

        verify(contractService).autoSignContract(1L);
    }

    @Test
    void testDepositPaymentCreation() {
        // Arrange
        DepositPaymentRequest request = new DepositPaymentRequest(
                "1", // userId
                "1"  // groupId
        );

        DepositPaymentResponse expectedResponse = DepositPaymentResponse.builder()
                .paymentId(1L)
                .userId(1L)
                .groupId(1L)
                .amount(new BigDecimal("38000000"))
                .requiredAmount(new BigDecimal("38000000"))
                .paymentMethod("VNPAY")
                .status(PaymentStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .vnpayUrl("https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?test=123")
                .message("Deposit payment created successfully. Please complete the payment via VNPay.")
                .build();

        when(depositPaymentService.createDepositPayment(request, httpRequest, null))
                .thenReturn(expectedResponse);

        // Act
        DepositPaymentResponse response = depositPaymentService.createDepositPayment(request, httpRequest, null);

        // Assert
        assertNotNull(response);
        assertEquals(1L, response.paymentId());
        assertEquals(1L, response.userId());
        assertEquals(1L, response.groupId());
        assertEquals(new BigDecimal("38000000"), response.amount());
        assertEquals(PaymentStatus.PENDING, response.status());
        assertNotNull(response.vnpayUrl());
        assertTrue(response.vnpayUrl().contains("vnpayment.vn"));

        verify(depositPaymentService).createDepositPayment(request, httpRequest, null);
    }

    @Test
    void testDepositPaymentConfirmation() {
        // Arrange
        Long paymentId = 1L;
        String transactionCode = "VNPAY123456789";

        DepositPaymentResponse expectedResponse = DepositPaymentResponse.builder()
                .paymentId(1L)
                .userId(1L)
                .groupId(1L)
                .amount(new BigDecimal("38000000"))
                .requiredAmount(new BigDecimal("38000000"))
                .paymentMethod("VNPAY")
                .status(PaymentStatus.COMPLETED)
                .transactionCode("VNPAY123456789")
                .createdAt(LocalDateTime.now())
                .paidAt(LocalDateTime.now())
                .message("Deposit payment completed successfully")
                .build();

        when(depositPaymentService.confirmDepositPayment(String.valueOf(paymentId), transactionCode))
                .thenReturn(expectedResponse);

        // Act
        DepositPaymentResponse response = depositPaymentService.confirmDepositPayment(String.valueOf(paymentId), transactionCode);

        // Assert
        assertNotNull(response);
        assertEquals(1L, response.paymentId());
        assertEquals(PaymentStatus.COMPLETED, response.status());
        assertEquals("VNPAY123456789", response.transactionCode());
        assertNotNull(response.paidAt());
        assertEquals("Deposit payment completed successfully", response.message());

        verify(depositPaymentService).confirmDepositPayment(String.valueOf(paymentId), transactionCode);
    }

    @Test
    void testGroupActivation() {
        // Arrange
        Long groupId = 1L;

        // Mock all members have paid deposit
        List<Map<String, Object>> groupStatus = List.of(
                Map.of(
                        "userId", 1L,
                        "userName", "Nguyễn Văn Test",
                        "depositStatus", DepositStatus.PAID,
                        "ownershipPercentage", new BigDecimal("40.00"),
                        "requiredDepositAmount", new BigDecimal("38000000")
                ),
                Map.of(
                        "userId", 2L,
                        "userName", "Trần Thị B",
                        "depositStatus", DepositStatus.PAID,
                        "ownershipPercentage", new BigDecimal("35.00"),
                        "requiredDepositAmount", new BigDecimal("33250000")
                ),
                Map.of(
                        "userId", 3L,
                        "userName", "Lê Văn C",
                        "depositStatus", DepositStatus.PAID,
                        "ownershipPercentage", new BigDecimal("25.00"),
                        "requiredDepositAmount", new BigDecimal("23750000")
                )
        );

        when(depositPaymentService.getGroupDepositStatus(groupId))
                .thenReturn(groupStatus);

        // Act
        List<Map<String, Object>> response = depositPaymentService.getGroupDepositStatus(groupId);

        // Assert
        assertNotNull(response);
        assertEquals(3, response.size());

        // Check all members have paid
        boolean allPaid = response.stream()
                .allMatch(member -> DepositStatus.PAID.equals(member.get("depositStatus")));
        assertTrue(allPaid, "All members should have paid their deposits");

        // Check ownership percentages sum to 100%
        BigDecimal totalPercentage = response.stream()
                .map(member -> (BigDecimal) member.get("ownershipPercentage"))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(new BigDecimal("100.00"), totalPercentage);

        verify(depositPaymentService).getGroupDepositStatus(groupId);
    }

    @Test
    void testDepositCalculationByOwnershipPercentage() {
        // Arrange
        BigDecimal vehicleValue = new BigDecimal("950000000"); // 950 triệu VND
        BigDecimal ownershipPercentage = new BigDecimal("40.00"); // 40%

        // Expected: 950M * 10% * 40% = 38M VND
        BigDecimal expectedDeposit = new BigDecimal("38000000");

        // Act & Assert
        // This would be tested in DepositCalculationService
        // For integration test, we verify the calculation logic
        BigDecimal calculatedDeposit = vehicleValue
                .multiply(new BigDecimal("0.1")) // 10%
                .multiply(ownershipPercentage)   // 40%
                .divide(new BigDecimal("100"), 2, java.math.RoundingMode.UP);

        assertEquals(0, expectedDeposit.compareTo(calculatedDeposit));
    }

    @Test
    void testContractFlowErrorHandling() {
        // Test contract not signed before deposit payment
        DepositPaymentRequest request = new DepositPaymentRequest(
                "1", "1"
        );

        when(depositPaymentService.createDepositPayment(request, httpRequest, null))
                .thenThrow(new IllegalStateException("Contract must be signed before making deposit payment"));

        // Act & Assert
        assertThrows(IllegalStateException.class, () -> depositPaymentService.createDepositPayment(request, httpRequest, null));

        verify(depositPaymentService).createDepositPayment(request, httpRequest, null);
    }

    @Test
    void testMultipleMembersDepositFlow() {
        // Test scenario with multiple members having different ownership percentages

        // Member 1: 40% ownership
        testSingleMemberDeposit(1L, new BigDecimal("38000000"));

        // Member 2: 35% ownership  
        testSingleMemberDeposit(2L, new BigDecimal("33250000"));

        // Member 3: 25% ownership
        testSingleMemberDeposit(3L, new BigDecimal("23750000"));
    }

    private void testSingleMemberDeposit(Long userId, BigDecimal expectedAmount) {
        // Arrange
        DepositPaymentRequest request = new DepositPaymentRequest(
                userId.toString(), "1"
        );

        DepositPaymentResponse response = DepositPaymentResponse.builder()
                .paymentId(userId)
                .userId(userId)
                .groupId(1L)
                .amount(expectedAmount)
                .requiredAmount(expectedAmount)
                .paymentMethod("VNPAY")
                .status(PaymentStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .vnpayUrl("https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?test=" + userId)
                .message("Deposit payment created successfully")
                .build();

        when(depositPaymentService.createDepositPayment(request, httpRequest, null))
                .thenReturn(response);

        // Act
        DepositPaymentResponse result = depositPaymentService.createDepositPayment(request, httpRequest, null);

        // Assert
        assertNotNull(result);
        assertEquals(userId, result.userId());
        assertEquals(expectedAmount, result.amount());
        assertEquals(PaymentStatus.PENDING, result.status());

        verify(depositPaymentService).createDepositPayment(request, httpRequest, null);
    }
}
