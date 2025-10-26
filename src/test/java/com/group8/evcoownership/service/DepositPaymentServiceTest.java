package com.group8.evcoownership.service;

import com.group8.evcoownership.dto.DepositPaymentRequest;
import com.group8.evcoownership.dto.DepositPaymentResponse;
import com.group8.evcoownership.entity.*;
import com.group8.evcoownership.enums.*;
import com.group8.evcoownership.exception.DepositPaymentException;
import com.group8.evcoownership.exception.PaymentConflictException;
import com.group8.evcoownership.repository.*;
import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DepositPaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PaymentService paymentService;

    @Mock
    private OwnershipShareRepository shareRepository;

    @Mock
    private ContractRepository contractRepository;

    @Mock
    private SharedFundRepository fundRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private OwnershipGroupRepository groupRepository;

    @Mock
    private VnPay_PaymentService vnPayPaymentService;

    @Mock
    private DepositCalculationService depositCalculationService;

    @Mock
    private VehicleRepository vehicleRepository;

    @Mock
    private NotificationOrchestrator notificationOrchestrator;

    @Mock
    private HttpServletRequest httpRequest;

    @InjectMocks
    private DepositPaymentService depositPaymentService;

    private OwnershipGroup testGroup;
    private User testUser;
    private OwnershipShare testShare;
    private Vehicle testVehicle;
    private Contract testContract;
    private SharedFund testFund;

    @BeforeEach
    void setUp() {
        // Setup test data
        testGroup = OwnershipGroup.builder()
                .groupId(1L)
                .groupName("Test EV Group")
                .status(GroupStatus.PENDING)
                .memberCapacity(3)
                .description("Test group for EV sharing")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        testUser = User.builder()
                .userId(1L)
                .fullName("Nguyễn Văn Test")
                .email("test@example.com")
                .phoneNumber("0901234567")
                .build();

        testShare = OwnershipShare.builder()
                .id(new OwnershipShareId(1L, 1L))
                .user(testUser)
                .group(testGroup)
                .groupRole(GroupRole.ADMIN)
                .ownershipPercentage(new BigDecimal("40.00"))
                .joinDate(LocalDateTime.now())
                .depositStatus(DepositStatus.PENDING)
                .updatedAt(LocalDateTime.now())
                .build();

        testVehicle = Vehicle.builder()
                .Id(1L)
                .brand("VinFast")
                .model("VF 8 Plus")
                .licensePlate("30A-123.45")
                .vehicleValue(new BigDecimal("950000000")) // 950 triệu VND
                .ownershipGroup(testGroup)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        testContract = Contract.builder()
                .id(1L)
                .group(testGroup)
                .startDate(java.time.LocalDate.now())
                .endDate(java.time.LocalDate.now().plusYears(1))
                .terms("Test contract terms [ĐÃ KÝ]")
                .requiredDepositAmount(new BigDecimal("38000000")) // 38 triệu VND
                .isActive(false)
                .approvalStatus(ContractApprovalStatus.SIGNED)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        testFund = SharedFund.builder()
                .fundId(1L)
                .group(testGroup)
                .balance(BigDecimal.ZERO)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    @Test
    void testCreateDepositPayment_Success() {
        // Arrange
        DepositPaymentRequest request = new DepositPaymentRequest(
                "1", // userId
                "1", // groupId
                new BigDecimal("38000000"), // amount
                "VNPAY"
        );

        Payment savedPayment = Payment.builder()
                .id(1L)
                .payer(testUser)
                .fund(testFund)
                .amount(request.amount())
                .paymentMethod(request.paymentMethod())
                .paymentType(PaymentType.CONTRIBUTION)
                .status(PaymentStatus.PENDING)
                .paymentCategory("GROUP")
                .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(groupRepository.findById(1L)).thenReturn(Optional.of(testGroup));
        when(shareRepository.findById(new OwnershipShareId(1L, 1L))).thenReturn(Optional.of(testShare));
        when(contractRepository.findByGroupGroupId(1L)).thenReturn(Optional.of(testContract));
        when(fundRepository.findByGroup_GroupId(1L)).thenReturn(Optional.of(testFund));
        when(paymentRepository.save(any(Payment.class))).thenReturn(savedPayment);
        when(vnPayPaymentService.createDepositPaymentUrl(anyLong(), any(HttpServletRequest.class)))
                .thenReturn("https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?test=123");
        when(depositCalculationService.calculateRequiredDepositAmount(any(Integer.class)))
                .thenReturn(new BigDecimal("38000000"));

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

        verify(paymentRepository).save(any(Payment.class));
        verify(vnPayPaymentService).createDepositPaymentUrl(anyLong(), any(HttpServletRequest.class));
    }

    @Test
    void testCreateDepositPayment_UserNotFound() {
        // Arrange
        DepositPaymentRequest request = new DepositPaymentRequest(
                "999", // non-existent userId
                "1",
                new BigDecimal("38000000"),
                "VNPAY"
        );

        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(EntityNotFoundException.class, () -> {
            depositPaymentService.createDepositPayment(request, httpRequest);
        });

        verify(userRepository).findById(999L);
        verify(paymentRepository, never()).save(any(Payment.class));
    }

    @Test
    void testCreateDepositPayment_ContractNotSigned() {
        // Arrange
        DepositPaymentRequest request = new DepositPaymentRequest(
                "1", "1", new BigDecimal("38000000"), "VNPAY"
        );

        Contract unsignedContract = Contract.builder()
                .id(1L)
                .group(testGroup)
                .terms("Test contract terms") // No [ĐÃ KÝ] marker
                .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(groupRepository.findById(1L)).thenReturn(Optional.of(testGroup));
        when(shareRepository.findById(new OwnershipShareId(1L, 1L))).thenReturn(Optional.of(testShare));
        when(contractRepository.findByGroupGroupId(1L)).thenReturn(Optional.of(unsignedContract));

        // Act & Assert
        assertThrows(DepositPaymentException.class, () -> {
            depositPaymentService.createDepositPayment(request, httpRequest);
        });

        verify(userRepository).findById(1L);
        verify(paymentRepository, never()).save(any(Payment.class));
    }

    @Test
    void testCreateDepositPayment_AlreadyPaid() {
        // Arrange
        DepositPaymentRequest request = new DepositPaymentRequest(
                "1", "1", new BigDecimal("38000000"), "VNPAY"
        );

        OwnershipShare paidShare = OwnershipShare.builder()
                .id(new OwnershipShareId(1L, 1L))
                .user(testUser)
                .group(testGroup)
                .depositStatus(DepositStatus.PAID) // Already paid
                .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(groupRepository.findById(1L)).thenReturn(Optional.of(testGroup));
        when(shareRepository.findById(new OwnershipShareId(1L, 1L))).thenReturn(Optional.of(paidShare));
        when(contractRepository.findByGroupGroupId(1L)).thenReturn(Optional.of(testContract));

        // Act & Assert
        assertThrows(PaymentConflictException.class, () -> {
            depositPaymentService.createDepositPayment(request, httpRequest);
        });

        verify(userRepository).findById(1L);
        verify(paymentRepository, never()).save(any(Payment.class));
    }

    @Test
    void testCreateDepositPayment_WrongAmount() {
        // Arrange
        DepositPaymentRequest request = new DepositPaymentRequest(
                "1", "1", new BigDecimal("50000000"), // Wrong amount
                "VNPAY"
        );

        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(groupRepository.findById(1L)).thenReturn(Optional.of(testGroup));
        when(shareRepository.findById(new OwnershipShareId(1L, 1L))).thenReturn(Optional.of(testShare));
        when(contractRepository.findByGroupGroupId(1L)).thenReturn(Optional.of(testContract));
        when(depositCalculationService.calculateRequiredDepositAmount(any(Integer.class)))
                .thenReturn(new BigDecimal("38000000")); // Correct amount

        // Act & Assert
        assertThrows(DepositPaymentException.class, () -> {
            depositPaymentService.createDepositPayment(request, httpRequest);
        });

        verify(userRepository).findById(1L);
        verify(paymentRepository, never()).save(any(Payment.class));
    }

    @Test
    void testConfirmDepositPayment_Success() {
        // Arrange
        Long paymentId = 1L;
        String transactionCode = "VNPAY123456789";

        Payment payment = Payment.builder()
                .id(paymentId)
                .payer(testUser)
                .fund(testFund)
                .amount(new BigDecimal("38000000"))
                .status(PaymentStatus.PENDING)
                .build();

        when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(payment));
        when(shareRepository.findById(new OwnershipShareId(1L, 1L))).thenReturn(Optional.of(testShare));
        when(shareRepository.save(any(OwnershipShare.class))).thenReturn(testShare);
        when(paymentService.updateStatus(paymentId, PaymentStatus.COMPLETED, transactionCode, null))
                .thenReturn(null); // Mock the updateStatus call
        when(shareRepository.findByGroupGroupId(1L)).thenReturn(List.of(testShare));
        when(contractRepository.findByGroupGroupId(1L)).thenReturn(Optional.of(testContract));

        // Act
        DepositPaymentResponse response = depositPaymentService.confirmDepositPayment(paymentId, transactionCode);

        // Assert
        assertNotNull(response);
        assertEquals(paymentId, response.paymentId());
        assertEquals(1L, response.userId());
        assertEquals(1L, response.groupId());
        assertEquals(PaymentStatus.COMPLETED, response.status());
        assertEquals(transactionCode, response.transactionCode());
        assertNotNull(response.paidAt());

        verify(paymentService).updateStatus(paymentId, PaymentStatus.COMPLETED, transactionCode, null);
        verify(shareRepository).save(any(OwnershipShare.class));
    }

    @Test
    void testGetDepositInfo_Success() {
        // Arrange
        Long userId = 1L;
        Long groupId = 1L;

        when(shareRepository.findById(new OwnershipShareId(userId, groupId))).thenReturn(Optional.of(testShare));
        when(contractRepository.findByGroupGroupId(groupId)).thenReturn(Optional.of(testContract));
        when(depositCalculationService.calculateRequiredDepositAmount(any(Integer.class)))
                .thenReturn(new BigDecimal("38000000"));

        // Act
        Map<String, Object> info = depositPaymentService.getDepositInfo(userId, groupId);

        // Assert
        assertNotNull(info);
        assertEquals(userId, info.get("userId"));
        assertEquals(groupId, info.get("groupId"));
        assertEquals(DepositStatus.PENDING, info.get("depositStatus"));
        assertEquals(new BigDecimal("38000000"), info.get("requiredAmount"));
        assertEquals(new BigDecimal("40.00"), info.get("ownershipPercentage"));
        assertTrue((Boolean) info.get("contractSigned"));
        assertTrue((Boolean) info.get("canPay"));

        verify(shareRepository).findById(new OwnershipShareId(userId, groupId));
        verify(contractRepository).findByGroupGroupId(groupId);
    }

    @Test
    void testGetGroupDepositStatus_Success() {
        // Arrange
        Long groupId = 1L;
        List<OwnershipShare> shares = List.of(testShare);

        when(shareRepository.findByGroupGroupId(groupId)).thenReturn(shares);
        when(groupRepository.findById(groupId)).thenReturn(Optional.of(testGroup));
        when(depositCalculationService.calculateRequiredDepositAmount(any(Integer.class)))
                .thenReturn(new BigDecimal("38000000"));

        // Act
        List<Map<String, Object>> statusList = depositPaymentService.getGroupDepositStatus(groupId);

        // Assert
        assertNotNull(statusList);
        assertEquals(1, statusList.size());

        Map<String, Object> status = statusList.get(0);
        assertEquals(1L, status.get("userId"));
        assertEquals("Nguyễn Văn Test", status.get("userName"));
        assertEquals("test@example.com", status.get("userEmail"));
        assertEquals(DepositStatus.PENDING, status.get("depositStatus"));
        assertEquals(new BigDecimal("40.00"), status.get("ownershipPercentage"));
        assertEquals(new BigDecimal("38000000"), status.get("requiredDepositAmount"));

        verify(shareRepository).findByGroupGroupId(groupId);
        verify(groupRepository).findById(groupId);
    }

    @Test
    void testDepositCalculationByOwnershipPercentage() {
        // Arrange
        BigDecimal vehicleValue = new BigDecimal("950000000"); // 950 triệu VND
        BigDecimal ownershipPercentage = new BigDecimal("40.00"); // 40%
        BigDecimal expectedDeposit = new BigDecimal("38000000"); // 38 triệu VND

        when(vehicleRepository.findByOwnershipGroup(testGroup)).thenReturn(Optional.of(testVehicle));
        when(depositCalculationService.calculateRequiredDepositAmount(vehicleValue, ownershipPercentage))
                .thenReturn(expectedDeposit);

        // Act - Sử dụng reflection để test private method
        try {
            java.lang.reflect.Method method = DepositPaymentService.class.getDeclaredMethod(
                    "calculateDepositAmountForUser", OwnershipGroup.class, OwnershipShare.class);
            method.setAccessible(true);
            BigDecimal result = (BigDecimal) method.invoke(depositPaymentService, testGroup, testShare);

            // Assert
            assertEquals(expectedDeposit, result);
            verify(depositCalculationService).calculateRequiredDepositAmount(vehicleValue, ownershipPercentage);
        } catch (Exception e) {
            fail("Failed to invoke private method: " + e.getMessage());
        }
    }

    @Test
    void testDepositCalculationFallbackToCapacity() {
        // Arrange
        when(vehicleRepository.findByOwnershipGroup(testGroup)).thenReturn(Optional.empty());
        when(depositCalculationService.calculateRequiredDepositAmount(testGroup.getMemberCapacity()))
                .thenReturn(new BigDecimal("2000000"));

        // Act - Sử dụng reflection để test private method
        try {
            java.lang.reflect.Method method = DepositPaymentService.class.getDeclaredMethod(
                    "calculateDepositAmountForUser", OwnershipGroup.class, OwnershipShare.class);
            method.setAccessible(true);
            BigDecimal result = (BigDecimal) method.invoke(depositPaymentService, testGroup, testShare);

            // Assert
            assertEquals(new BigDecimal("2000000"), result);
            verify(depositCalculationService).calculateRequiredDepositAmount(testGroup.getMemberCapacity());
        } catch (Exception e) {
            fail("Failed to invoke private method: " + e.getMessage());
        }
    }
}
