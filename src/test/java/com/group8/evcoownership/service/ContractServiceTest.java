package com.group8.evcoownership.service;

import com.group8.evcoownership.dto.AutoSignConditionsResponseDTO;
import com.group8.evcoownership.dto.ContractInfoResponseDTO;
import com.group8.evcoownership.entity.*;
import com.group8.evcoownership.enums.ContractApprovalStatus;
import com.group8.evcoownership.enums.GroupRole;
import com.group8.evcoownership.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ContractServiceTest {

    @Mock
    private ContractRepository contractRepository;

    @Mock
    private OwnershipGroupRepository groupRepository;

    @Mock
    private OwnershipShareRepository ownershipShareRepository;

    @Mock
    private VehicleRepository vehicleRepository;

    @Mock
    private DepositCalculationService depositCalculationService;

    @Mock
    private NotificationOrchestrator notificationOrchestrator;

    @Mock
    private ContractFeedbackRepository feedbackRepository;

    @Mock
    private ContractHelperService contractHelperService;

    @InjectMocks
    private ContractService contractService;

    private OwnershipGroup testGroup;
    private Contract testContract;
    private final Long TEST_GROUP_ID = 1L;
    private final Long TEST_CONTRACT_ID = 1L;

    @BeforeEach
    void setUp() {
        // Setup test group
        testGroup = OwnershipGroup.builder()
                .groupId(TEST_GROUP_ID)
                .groupName("Test EV Group")
                .description("Test group for EV co-ownership")
                .memberCapacity(5)
                .build();

        // Setup test contract
        testContract = Contract.builder()
                .id(TEST_CONTRACT_ID)
                .group(testGroup)
                .startDate(LocalDate.now())
                .endDate(LocalDate.now().plusYears(1))
                .terms("Test contract terms")
                .requiredDepositAmount(new BigDecimal("2000000"))
                .isActive(true)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }


    @Test
    void cancelContract_Success() {
        // Given
        String reason = "Group members decided to cancel";

        // Mock group repository
        when(groupRepository.findById(TEST_GROUP_ID))
                .thenReturn(Optional.of(testGroup));

        // Mock contract repository - contract đã tồn tại
        when(contractRepository.findByGroupGroupId(TEST_GROUP_ID))
                .thenReturn(Optional.of(testContract));

        // Mock admin share
        User adminUser = User.builder()
                .userId(1L)
                .email("admin@test.com")
                .fullName("Admin User")
                .build();

        OwnershipShare adminShare = OwnershipShare.builder()
                .group(testGroup)
                .user(adminUser)
                .groupRole(GroupRole.ADMIN)
                .ownershipPercentage(new BigDecimal("20.00"))
                .build();

        when(ownershipShareRepository.findByGroup_GroupId(TEST_GROUP_ID))
                .thenReturn(List.of(adminShare));

        // Mock feedback repository
        when(feedbackRepository.save(any(ContractFeedback.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        contractService.cancelContract(TEST_GROUP_ID, reason);

        // Then
        verify(groupRepository).findById(TEST_GROUP_ID);
        verify(contractRepository).findByGroupGroupId(TEST_GROUP_ID);
        verify(ownershipShareRepository).findByGroup_GroupId(TEST_GROUP_ID);
        verify(feedbackRepository).save(any(ContractFeedback.class));
    }

    @Test
    void getContractInfo_Success() {
        // Given
        when(groupRepository.findById(TEST_GROUP_ID)).thenReturn(Optional.of(testGroup));
        when(contractRepository.findByGroupGroupId(TEST_GROUP_ID))
                .thenReturn(Optional.of(testContract));

        // When
        ContractInfoResponseDTO result = contractService.getContractInfo(TEST_GROUP_ID);

        // Then
        assertNotNull(result);
        assertEquals(TEST_CONTRACT_ID, result.getContractId());
        assertEquals(TEST_GROUP_ID, result.getGroupId());
        assertEquals("Test EV Group", result.getGroupName());
        assertEquals(testContract.getStartDate(), result.getStartDate());
        assertEquals(testContract.getEndDate(), result.getEndDate());
        assertEquals(testContract.getTerms(), result.getTerms());
        assertEquals(testContract.getRequiredDepositAmount(), result.getRequiredDepositAmount());
        assertEquals(testContract.getIsActive(), result.getIsActive());
        assertEquals(testContract.getCreatedAt(), result.getCreatedAt());
        assertEquals(testContract.getUpdatedAt(), result.getUpdatedAt());

        verify(groupRepository).findById(TEST_GROUP_ID);
        verify(contractRepository).findByGroupGroupId(TEST_GROUP_ID);
    }


    @Test
    void autoSignContract_AlreadySigned_ShouldThrowException() {
        // Given
        // Setup contract that is already signed
        testContract.setApprovalStatus(ContractApprovalStatus.SIGNED);

        when(groupRepository.findById(TEST_GROUP_ID))
                .thenReturn(Optional.of(testGroup));
        when(contractRepository.findByGroup(testGroup))
                .thenReturn(Optional.of(testContract));

        // When & Then
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            contractService.autoSignContract(TEST_GROUP_ID);
        });

        assertEquals("Contract has already been signed and cannot be auto-signed again", exception.getMessage());
        verify(groupRepository).findById(TEST_GROUP_ID);
        verify(contractRepository).findByGroup(testGroup);
        verify(contractRepository, never()).saveAndFlush(any(Contract.class));
    }

    @Test
    void autoSignContract_AlreadyApproved_ShouldThrowException() {
        // Given
        // Setup contract that is already approved
        testContract.setApprovalStatus(ContractApprovalStatus.APPROVED);

        when(groupRepository.findById(TEST_GROUP_ID))
                .thenReturn(Optional.of(testGroup));
        when(contractRepository.findByGroup(testGroup))
                .thenReturn(Optional.of(testContract));

        // When & Then
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            contractService.autoSignContract(TEST_GROUP_ID);
        });

        assertEquals("Contract has already been signed and cannot be auto-signed again", exception.getMessage());
        verify(groupRepository).findById(TEST_GROUP_ID);
        verify(contractRepository).findByGroup(testGroup);
        verify(contractRepository, never()).saveAndFlush(any(Contract.class));
    }

    @Test
    void generateContractData_InvalidOwnershipPercentage_ShouldThrowException() {
        // Given
        when(groupRepository.findById(TEST_GROUP_ID))
                .thenReturn(Optional.of(testGroup));

        // Mock shares với đủ số thành viên nhưng tổng tỷ lệ không bằng 100%
        OwnershipShare share1 = OwnershipShare.builder()
                .group(testGroup)
                .user(User.builder().userId(1L).build())
                .ownershipPercentage(new BigDecimal("40.00"))
                .build();

        OwnershipShare share2 = OwnershipShare.builder()
                .group(testGroup)
                .user(User.builder().userId(2L).build())
                .ownershipPercentage(new BigDecimal("30.00"))
                .build();

        OwnershipShare share3 = OwnershipShare.builder()
                .group(testGroup)
                .user(User.builder().userId(3L).build())
                .ownershipPercentage(new BigDecimal("20.00"))
                .build();

        OwnershipShare share4 = OwnershipShare.builder()
                .group(testGroup)
                .user(User.builder().userId(4L).build())
                .ownershipPercentage(new BigDecimal("5.00"))
                .build();

        OwnershipShare share5 = OwnershipShare.builder()
                .group(testGroup)
                .user(User.builder().userId(5L).build())
                .ownershipPercentage(new BigDecimal("3.00"))
                .build();

        // Tổng chỉ có 98%, thiếu 2%
        when(ownershipShareRepository.findByGroupGroupId(TEST_GROUP_ID))
                .thenReturn(List.of(share1, share2, share3, share4, share5));

        // When & Then
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            contractService.generateContractData(TEST_GROUP_ID, 1L);
        });

        assertEquals("Cannot generate contract: Total ownership percentage must be exactly 100%, but found 98.00%. Please adjust ownership percentages.", exception.getMessage());
        verify(groupRepository).findById(TEST_GROUP_ID);
        verify(ownershipShareRepository).findByGroupGroupId(TEST_GROUP_ID);
    }

    @Test
    void generateContractData_ZeroOwnershipPercentage_ShouldThrowException() {
        // Given
        when(groupRepository.findById(TEST_GROUP_ID))
                .thenReturn(Optional.of(testGroup));

        // Mock shares với đủ số thành viên nhưng một thành viên có 0% ownership
        OwnershipShare share1 = OwnershipShare.builder()
                .group(testGroup)
                .user(User.builder().userId(1L).fullName("John Doe").build())
                .ownershipPercentage(new BigDecimal("50.00"))
                .build();

        OwnershipShare share2 = OwnershipShare.builder()
                .group(testGroup)
                .user(User.builder().userId(2L).fullName("Jane Smith").build())
                .ownershipPercentage(new BigDecimal("0.00")) // 0% ownership - should fail
                .build();

        OwnershipShare share3 = OwnershipShare.builder()
                .group(testGroup)
                .user(User.builder().userId(3L).fullName("Bob Wilson").build())
                .ownershipPercentage(new BigDecimal("25.00"))
                .build();

        OwnershipShare share4 = OwnershipShare.builder()
                .group(testGroup)
                .user(User.builder().userId(4L).fullName("Alice Brown").build())
                .ownershipPercentage(new BigDecimal("15.00"))
                .build();

        OwnershipShare share5 = OwnershipShare.builder()
                .group(testGroup)
                .user(User.builder().userId(5L).fullName("Charlie Davis").build())
                .ownershipPercentage(new BigDecimal("10.00"))
                .build();

        when(ownershipShareRepository.findByGroupGroupId(TEST_GROUP_ID))
                .thenReturn(List.of(share1, share2, share3, share4, share5));

        // When & Then
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            contractService.generateContractData(TEST_GROUP_ID, 1L);
        });

        assertEquals("Cannot generate contract: Member Jane Smith has 0% ownership. All members must have ownership percentage > 0%.", exception.getMessage());
        verify(groupRepository).findById(TEST_GROUP_ID);
        verify(ownershipShareRepository).findByGroupGroupId(TEST_GROUP_ID);
    }

    @Test
    void generateContractData_NullOwnershipPercentage_ShouldThrowException() {
        // Given
        when(groupRepository.findById(TEST_GROUP_ID))
                .thenReturn(Optional.of(testGroup));

        // Mock shares với đủ số thành viên nhưng một thành viên có null ownership percentage
        OwnershipShare share1 = OwnershipShare.builder()
                .group(testGroup)
                .user(User.builder().userId(1L).fullName("John Doe").build())
                .ownershipPercentage(new BigDecimal("50.00"))
                .build();

        OwnershipShare share2 = OwnershipShare.builder()
                .group(testGroup)
                .user(User.builder().userId(2L).fullName("Jane Smith").build())
                .ownershipPercentage(null) // null ownership - should fail
                .build();

        OwnershipShare share3 = OwnershipShare.builder()
                .group(testGroup)
                .user(User.builder().userId(3L).fullName("Bob Wilson").build())
                .ownershipPercentage(new BigDecimal("25.00"))
                .build();

        OwnershipShare share4 = OwnershipShare.builder()
                .group(testGroup)
                .user(User.builder().userId(4L).fullName("Alice Brown").build())
                .ownershipPercentage(new BigDecimal("15.00"))
                .build();

        OwnershipShare share5 = OwnershipShare.builder()
                .group(testGroup)
                .user(User.builder().userId(5L).fullName("Charlie Davis").build())
                .ownershipPercentage(new BigDecimal("10.00"))
                .build();

        when(ownershipShareRepository.findByGroupGroupId(TEST_GROUP_ID))
                .thenReturn(List.of(share1, share2, share3, share4, share5));

        // When & Then
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            contractService.generateContractData(TEST_GROUP_ID, 1L);
        });

        assertEquals("Cannot generate contract: Member Jane Smith has null ownership percentage. All members must have ownership percentage set.", exception.getMessage());
        verify(groupRepository).findById(TEST_GROUP_ID);
        verify(ownershipShareRepository).findByGroupGroupId(TEST_GROUP_ID);
    }

    @Test
    void checkAutoSignConditions_InvalidOwnershipPercentage_ShouldReturnFalse() {
        // Given
        when(groupRepository.findById(TEST_GROUP_ID))
                .thenReturn(Optional.of(testGroup));

        // Mock shares với đủ số thành viên nhưng tổng tỷ lệ không bằng 100%
        OwnershipShare share1 = OwnershipShare.builder()
                .group(testGroup)
                .user(User.builder().userId(1L).build())
                .ownershipPercentage(new BigDecimal("50.00"))
                .build();

        OwnershipShare share2 = OwnershipShare.builder()
                .group(testGroup)
                .user(User.builder().userId(2L).build())
                .ownershipPercentage(new BigDecimal("30.00"))
                .build();

        OwnershipShare share3 = OwnershipShare.builder()
                .group(testGroup)
                .user(User.builder().userId(3L).build())
                .ownershipPercentage(new BigDecimal("15.00"))
                .build();

        OwnershipShare share4 = OwnershipShare.builder()
                .group(testGroup)
                .user(User.builder().userId(4L).build())
                .ownershipPercentage(new BigDecimal("3.00"))
                .build();

        OwnershipShare share5 = OwnershipShare.builder()
                .group(testGroup)
                .user(User.builder().userId(5L).build())
                .ownershipPercentage(new BigDecimal("1.00"))
                .build();

        // Tổng chỉ có 99%, thiếu 1%
        when(ownershipShareRepository.findByGroupGroupId(TEST_GROUP_ID))
                .thenReturn(List.of(share1, share2, share3, share4, share5));

        // Mock vehicle repository
        when(vehicleRepository.findByOwnershipGroup(testGroup))
                .thenReturn(Optional.empty());

        // When
        AutoSignConditionsResponseDTO conditions = contractService.checkAutoSignConditions(TEST_GROUP_ID);

        // Then
        assertFalse(Boolean.TRUE.equals(conditions.getHasCorrectOwnershipPercentage()));
        assertFalse(Boolean.TRUE.equals(conditions.getCanAutoSign()));
        assertEquals(new BigDecimal("99.00"), conditions.getTotalOwnershipPercentage());
        assertEquals(new BigDecimal("100.00"), conditions.getExpectedOwnershipPercentage());

        verify(groupRepository).findById(TEST_GROUP_ID);
        verify(ownershipShareRepository).findByGroupGroupId(TEST_GROUP_ID);
        verify(vehicleRepository).findByOwnershipGroup(testGroup);
    }
}
