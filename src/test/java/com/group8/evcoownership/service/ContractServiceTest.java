package com.group8.evcoownership.service;

import com.group8.evcoownership.entity.Contract;
import com.group8.evcoownership.entity.OwnershipGroup;
import com.group8.evcoownership.entity.OwnershipShare;
import com.group8.evcoownership.entity.User;
import com.group8.evcoownership.enums.DepositStatus;
import com.group8.evcoownership.enums.GroupRole;
import com.group8.evcoownership.repository.ContractRepository;
import com.group8.evcoownership.repository.OwnershipGroupRepository;
import com.group8.evcoownership.repository.OwnershipShareRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    private DepositCalculationService depositCalculationService;

    @InjectMocks
    private ContractService contractService;

    private OwnershipGroup testGroup;
    private Contract testContract;
    private OwnershipShare testShare;
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

        // Setup test share
        testShare = OwnershipShare.builder()
                .group(testGroup)
                .user(User.builder().userId(1L).build())
                .ownershipPercentage(new BigDecimal("20.00"))
                .groupRole(GroupRole.ADMIN)
                .depositStatus(DepositStatus.PENDING)
                .joinDate(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }






    @Test
    void getRequiredDepositAmount_Success() {
        // Given
        BigDecimal expectedAmount = new BigDecimal("2000000");
        when(groupRepository.findById(TEST_GROUP_ID)).thenReturn(Optional.of(testGroup));
        when(contractRepository.findByGroupGroupId(TEST_GROUP_ID))
                .thenReturn(Optional.of(testContract));

        // When
        BigDecimal result = contractService.getRequiredDepositAmount(TEST_GROUP_ID);

        // Then
        assertEquals(expectedAmount, result);
        verify(groupRepository).findById(TEST_GROUP_ID);
        verify(contractRepository).findByGroupGroupId(TEST_GROUP_ID);
    }



    @Test
    void cancelContract_Success() {
        // Given
        String reason = "Group members decided to cancel";
        when(contractRepository.findByGroupGroupId(TEST_GROUP_ID))
                .thenReturn(Optional.of(testContract));
        when(contractRepository.save(any(Contract.class))).thenReturn(testContract);

        // When
        contractService.cancelContract(TEST_GROUP_ID, reason);

        // Then
        verify(contractRepository).findByGroupGroupId(TEST_GROUP_ID);
        verify(contractRepository).save(any(Contract.class));
    }

    @Test
    void getContractInfo_Success() {
        // Given
        when(groupRepository.findById(TEST_GROUP_ID)).thenReturn(Optional.of(testGroup));
        when(contractRepository.findByGroupGroupId(TEST_GROUP_ID))
                .thenReturn(Optional.of(testContract));

        // When
        Map<String, Object> result = contractService.getContractInfo(TEST_GROUP_ID);

        // Then
        assertNotNull(result);
        assertEquals(TEST_CONTRACT_ID, result.get("contractId"));
        assertEquals(TEST_GROUP_ID, result.get("groupId"));
        assertEquals("Test EV Group", result.get("groupName"));
        assertEquals(testContract.getStartDate(), result.get("startDate"));
        assertEquals(testContract.getEndDate(), result.get("endDate"));
        assertEquals(testContract.getTerms(), result.get("terms"));
        assertEquals(testContract.getRequiredDepositAmount(), result.get("requiredDepositAmount"));
        assertEquals(testContract.getIsActive(), result.get("isActive"));
        assertEquals(testContract.getCreatedAt(), result.get("createdAt"));
        assertEquals(testContract.getUpdatedAt(), result.get("updatedAt"));

        verify(groupRepository).findById(TEST_GROUP_ID);
        verify(contractRepository).findByGroupGroupId(TEST_GROUP_ID);
    }

    @Test
    void signContract_Success() {
        // Given
        Map<String, Object> contractData = new HashMap<>();
        contractData.put("terms", "Test contract terms");
        contractData.put("startDate", "2025-01-01");
        contractData.put("endDate", "2026-01-01");
        contractData.put("adminName", "Admin User");
        contractData.put("signatureType", "ADMIN_PROXY");

        when(groupRepository.findById(TEST_GROUP_ID))
                .thenReturn(Optional.of(testGroup));
        when(contractRepository.findByGroup(testGroup))
                .thenReturn(Optional.of(testContract));
        when(contractRepository.save(any(Contract.class))).thenReturn(testContract);

        // When
        Map<String, Object> result = contractService.signContractWithData(TEST_GROUP_ID, contractData);

        // Then
        assertNotNull(result);
        assertTrue((Boolean) result.get("success"));
        assertEquals(TEST_CONTRACT_ID, result.get("contractId"));
        assertEquals("SIGNED", result.get("status"));
        assertEquals("Contract has been signed successfully", result.get("message"));
        assertNotNull(result.get("signedAt"));

        verify(groupRepository).findById(TEST_GROUP_ID);
        verify(contractRepository).findByGroup(testGroup);
        verify(contractRepository).save(any(Contract.class));
    }

    @Test
    void signContractWithData_ContractNotActive() {
        // Given
        Map<String, Object> contractData = new HashMap<>();
        contractData.put("terms", "Test contract terms");
        contractData.put("startDate", "2025-01-01");
        contractData.put("endDate", "2026-01-01");
        contractData.put("adminName", "Admin User");
        contractData.put("signatureType", "ADMIN_PROXY");

        when(groupRepository.findById(TEST_GROUP_ID))
                .thenReturn(Optional.of(testGroup));
        when(contractRepository.findByGroup(testGroup))
                .thenReturn(Optional.empty()); // No existing contract
        when(ownershipShareRepository.findByGroupGroupId(TEST_GROUP_ID))
                .thenReturn(List.of(testShare, testShare, testShare, testShare, testShare)); // Mock 5 shares to pass validation
        when(contractRepository.save(any(Contract.class))).thenReturn(testContract);

        // When
        Map<String, Object> result = contractService.signContractWithData(TEST_GROUP_ID, contractData);

        // Then
        assertNotNull(result);
        assertTrue((Boolean) result.get("success"));
        assertEquals(TEST_CONTRACT_ID, result.get("contractId"));
        assertEquals("SIGNED", result.get("status"));
        assertEquals("Contract has been signed successfully", result.get("message"));

        verify(groupRepository, times(2)).findById(TEST_GROUP_ID);
        verify(contractRepository).findByGroup(testGroup);
        verify(contractRepository).save(any(Contract.class));
    }
}
