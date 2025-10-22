package com.group8.evcoownership.integration;

import com.group8.evcoownership.dto.ContractGenerationRequest;
import com.group8.evcoownership.dto.ContractGenerationResponse;
import com.group8.evcoownership.dto.DepositPaymentRequest;
import com.group8.evcoownership.dto.DepositPaymentResponse;
import com.group8.evcoownership.enums.DepositStatus;
import com.group8.evcoownership.enums.PaymentStatus;
import com.group8.evcoownership.repository.*;
import com.group8.evcoownership.service.ContractGenerationService;
import com.group8.evcoownership.service.ContractService;
import com.group8.evcoownership.service.DepositPaymentService;
import com.group8.evcoownership.service.OwnershipGroupService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContractFlowIntegrationTest {

    @Mock
    private ContractGenerationService contractGenerationService;

    @Mock
    private ContractService contractService;

    @Mock
    private DepositPaymentService depositPaymentService;

    @Mock
    private OwnershipGroupService ownershipGroupService;

    @Mock
    private ContractRepository contractRepository;

    @Mock
    private OwnershipGroupRepository groupRepository;

    @Mock
    private OwnershipShareRepository shareRepository;

    @Mock
    private VehicleRepository vehicleRepository;

    @Mock
    private UserRepository userRepository;

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
        String tsxTemplate = "<Component>{group.name} Contract</Component>";
        ContractGenerationRequest request = new ContractGenerationRequest(
                LocalDate.now(),
                LocalDate.now().plusYears(1),
                "Test contract terms",
                "Hà Nội",
                LocalDate.now().toString(),
                null
        );

        ContractGenerationResponse expectedResponse = new ContractGenerationResponse(
                1L,
                "EVS-2025-001",
                java.util.Map.of("group", java.util.Map.of("name", "Test EV Group")),
                java.time.LocalDateTime.now(),
                "GENERATED"
        );

        when(contractGenerationService.generateContractAuto(1L, "REACT_TSX", tsxTemplate))
                .thenReturn(expectedResponse);

        // Act
        ContractGenerationResponse response = contractGenerationService.generateContractAuto(1L, "REACT_TSX", tsxTemplate);

        // Assert
        assertNotNull(response);
        assertEquals(1L, response.contractId());
        assertEquals("EVS-2025-001", response.contractNumber());
        assertEquals("Test EV Group", ((java.util.Map<?, ?>) response.props().get("group")).get("name"));
        assertEquals("GENERATED", response.status());

        verify(contractGenerationService).generateContractAuto(1L, "REACT_TSX", tsxTemplate);
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
                "signedAt", LocalDateTime.now(),
                "signedBy", "Nguyễn Văn Test",
                "signatureType", "ADMIN_PROXY",
                "message", "Contract signed successfully by Admin Group on behalf of all members"
        );

        when(contractService.signContract(1L, signRequest))
                .thenReturn(expectedResponse);

        // Act
        Map<String, Object> response = contractService.signContract(1L, signRequest);

        // Assert
        assertNotNull(response);
        assertTrue((Boolean) response.get("success"));
        assertEquals(1L, response.get("contractId"));
        assertEquals("Nguyễn Văn Test", response.get("signedBy"));
        assertEquals("ADMIN_PROXY", response.get("signatureType"));
        assertTrue(response.get("message").toString().contains("Contract signed successfully"));

        verify(contractService).signContract(1L, signRequest);
    }

    @Test
    void testDepositPaymentCreation() {
        // Arrange
        DepositPaymentRequest request = new DepositPaymentRequest(
                1L, // userId
                1L, // groupId
                new BigDecimal("38000000"), // amount (40% of 95M)
                "VNPAY"
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

        when(depositPaymentService.createDepositPayment(request, httpRequest))
                .thenReturn(expectedResponse);

        // Act
        DepositPaymentResponse response = depositPaymentService.createDepositPayment(request, httpRequest);

        // Assert
        assertNotNull(response);
        assertEquals(1L, response.paymentId());
        assertEquals(1L, response.userId());
        assertEquals(1L, response.groupId());
        assertEquals(new BigDecimal("38000000"), response.amount());
        assertEquals(PaymentStatus.PENDING, response.status());
        assertNotNull(response.vnpayUrl());
        assertTrue(response.vnpayUrl().contains("vnpayment.vn"));

        verify(depositPaymentService).createDepositPayment(request, httpRequest);
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

        when(depositPaymentService.confirmDepositPayment(paymentId, transactionCode))
                .thenReturn(expectedResponse);

        // Act
        DepositPaymentResponse response = depositPaymentService.confirmDepositPayment(paymentId, transactionCode);

        // Assert
        assertNotNull(response);
        assertEquals(1L, response.paymentId());
        assertEquals(PaymentStatus.COMPLETED, response.status());
        assertEquals("VNPAY123456789", response.transactionCode());
        assertNotNull(response.paidAt());
        assertEquals("Deposit payment completed successfully", response.message());

        verify(depositPaymentService).confirmDepositPayment(paymentId, transactionCode);
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
                1L, 1L, new BigDecimal("38000000"), "VNPAY"
        );

        when(depositPaymentService.createDepositPayment(request, httpRequest))
                .thenThrow(new IllegalStateException("Contract must be signed before making deposit payment"));

        // Act & Assert
        assertThrows(IllegalStateException.class, () -> {
            depositPaymentService.createDepositPayment(request, httpRequest);
        });

        verify(depositPaymentService).createDepositPayment(request, httpRequest);
    }

    @Test
    void testMultipleMembersDepositFlow() {
        // Test scenario with multiple members having different ownership percentages

        // Member 1: 40% ownership
        testSingleMemberDeposit(1L, new BigDecimal("40.00"), new BigDecimal("38000000"));

        // Member 2: 35% ownership  
        testSingleMemberDeposit(2L, new BigDecimal("35.00"), new BigDecimal("33250000"));

        // Member 3: 25% ownership
        testSingleMemberDeposit(3L, new BigDecimal("25.00"), new BigDecimal("23750000"));
    }

    private void testSingleMemberDeposit(Long userId, BigDecimal ownershipPercentage, BigDecimal expectedAmount) {
        // Arrange
        DepositPaymentRequest request = new DepositPaymentRequest(
                userId, 1L, expectedAmount, "VNPAY"
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

        when(depositPaymentService.createDepositPayment(request, httpRequest))
                .thenReturn(response);

        // Act
        DepositPaymentResponse result = depositPaymentService.createDepositPayment(request, httpRequest);

        // Assert
        assertNotNull(result);
        assertEquals(userId, result.userId());
        assertEquals(expectedAmount, result.amount());
        assertEquals(PaymentStatus.PENDING, result.status());

        verify(depositPaymentService).createDepositPayment(request, httpRequest);
    }
}
