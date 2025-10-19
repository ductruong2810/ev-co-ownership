package com.group8.evcoownership.service;

import com.group8.evcoownership.entity.Contract;
import com.group8.evcoownership.entity.OwnershipGroup;
import com.group8.evcoownership.repository.ContractRepository;
import com.group8.evcoownership.repository.OwnershipGroupRepository;
import jakarta.persistence.EntityNotFoundException;
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
    private DepositCalculationService depositCalculationService;

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
    void createDefaultContract_Success() {
        // Given
        Long newGroupId = TEST_GROUP_ID + 100; // Use different ID to avoid existing contract
        OwnershipGroup newGroup = OwnershipGroup.builder()
                .groupId(newGroupId)
                .groupName("New Test EV Group")
                .description("New test group for EV co-ownership")
                .memberCapacity(5)
                .build();

        Contract newContract = Contract.builder()
                .id(TEST_CONTRACT_ID + 1)
                .group(newGroup)
                .startDate(LocalDate.now())
                .endDate(LocalDate.now().plusYears(1))
                .terms("Standard EV co-ownership contract for 1 year")
                .requiredDepositAmount(new BigDecimal("2000000"))
                .isActive(true)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        when(groupRepository.findById(newGroupId)).thenReturn(Optional.of(newGroup));
        when(contractRepository.findByGroup(newGroup)).thenReturn(Optional.empty());
        when(depositCalculationService.calculateRequiredDepositAmount(newGroup))
                .thenReturn(new BigDecimal("2000000"));
        when(contractRepository.save(any(Contract.class))).thenReturn(newContract);

        // When
        Contract result = contractService.createDefaultContract(newGroupId);

        // Then
        assertNotNull(result);
        assertEquals(TEST_CONTRACT_ID + 1, result.getId());
        assertEquals("Standard EV co-ownership contract for 1 year", result.getTerms());
        assertTrue(result.getIsActive());
        assertNotNull(result.getStartDate());
        assertNotNull(result.getEndDate());
        assertEquals(result.getStartDate().plusYears(1), result.getEndDate());

        verify(groupRepository).findById(newGroupId);
        verify(contractRepository).findByGroup(newGroup);
        verify(depositCalculationService).calculateRequiredDepositAmount(newGroup);
        verify(contractRepository).save(any(Contract.class));
    }

    @Test
    void createDefaultContract_GroupNotFound() {
        // Given
        when(groupRepository.findById(TEST_GROUP_ID)).thenReturn(Optional.empty());

        // When & Then
        assertThrows(EntityNotFoundException.class, () ->
                contractService.createDefaultContract(TEST_GROUP_ID));

        verify(groupRepository).findById(TEST_GROUP_ID);
        verify(contractRepository, never()).save(any(Contract.class));
    }

    @Test
    void createDefaultContract_GroupAlreadyHasContract() {
        // Given
        when(groupRepository.findById(TEST_GROUP_ID)).thenReturn(Optional.of(testGroup));
        when(contractRepository.findByGroup(testGroup)).thenReturn(Optional.of(testContract));

        // When & Then
        assertThrows(IllegalStateException.class, () ->
                contractService.createDefaultContract(TEST_GROUP_ID));

        verify(groupRepository).findById(TEST_GROUP_ID);
        verify(contractRepository).findByGroup(testGroup);
        verify(contractRepository, never()).save(any(Contract.class));
    }

    @Test
    void createCustomContract_Success() {
        // Given
        LocalDate startDate = LocalDate.of(2025, 1, 1);
        LocalDate endDate = LocalDate.of(2025, 12, 31);
        String customTerms = "Custom contract terms";
        BigDecimal customAmount = new BigDecimal("3000000");

        when(groupRepository.findById(TEST_GROUP_ID)).thenReturn(Optional.of(testGroup));
        when(contractRepository.findByGroup(testGroup)).thenReturn(Optional.empty());
        when(contractRepository.save(any(Contract.class))).thenReturn(testContract);

        // When
        Contract result = contractService.createCustomContract(
                TEST_GROUP_ID, startDate, endDate, customTerms, customAmount);

        // Then
        assertNotNull(result);
        verify(groupRepository).findById(TEST_GROUP_ID);
        verify(contractRepository).findByGroup(testGroup);
        verify(contractRepository).save(any(Contract.class));
    }

    @Test
    void updateContract_Success() {
        // Given
        LocalDate newStartDate = LocalDate.of(2025, 2, 1);
        LocalDate newEndDate = LocalDate.of(2026, 2, 1);
        String newTerms = "Updated contract terms";
        BigDecimal newAmount = new BigDecimal("4000000");
        Boolean newIsActive = false;

        when(contractRepository.findByGroupGroupId(TEST_GROUP_ID))
                .thenReturn(Optional.of(testContract));
        when(contractRepository.save(any(Contract.class))).thenReturn(testContract);

        // When
        Contract result = contractService.updateContract(
                TEST_GROUP_ID, newStartDate, newEndDate, newTerms, newAmount, newIsActive);

        // Then
        assertNotNull(result);
        verify(contractRepository).findByGroupGroupId(TEST_GROUP_ID);
        verify(contractRepository).save(any(Contract.class));
    }

    @Test
    void updateContract_ContractNotFound() {
        // Given
        when(contractRepository.findByGroupGroupId(TEST_GROUP_ID))
                .thenReturn(Optional.empty());

        // When & Then
        assertThrows(EntityNotFoundException.class, () ->
                contractService.updateContract(TEST_GROUP_ID, LocalDate.now(),
                        LocalDate.now().plusYears(1), "terms", new BigDecimal("1000000"), true));

        verify(contractRepository).findByGroupGroupId(TEST_GROUP_ID);
        verify(contractRepository, never()).save(any(Contract.class));
    }

    @Test
    void getRequiredDepositAmount_Success() {
        // Given
        BigDecimal expectedAmount = new BigDecimal("2000000");
        when(contractRepository.findByGroupGroupId(TEST_GROUP_ID))
                .thenReturn(Optional.of(testContract));

        // When
        BigDecimal result = contractService.getRequiredDepositAmount(TEST_GROUP_ID);

        // Then
        assertEquals(expectedAmount, result);
        verify(contractRepository).findByGroupGroupId(TEST_GROUP_ID);
    }

    @Test
    void updateRequiredDepositAmount_Success() {
        // Given
        BigDecimal newAmount = new BigDecimal("5000000");
        when(contractRepository.findByGroupGroupId(TEST_GROUP_ID))
                .thenReturn(Optional.of(testContract));
        when(contractRepository.save(any(Contract.class))).thenReturn(testContract);

        // When
        Contract result = contractService.updateRequiredDepositAmount(TEST_GROUP_ID, newAmount);

        // Then
        assertNotNull(result);
        verify(contractRepository).findByGroupGroupId(TEST_GROUP_ID);
        verify(contractRepository).save(any(Contract.class));
    }

    @Test
    void recalculateRequiredDepositAmount_Success() {
        // Given
        BigDecimal newCalculatedAmount = new BigDecimal("3000000");
        when(groupRepository.findById(TEST_GROUP_ID)).thenReturn(Optional.of(testGroup));
        when(contractRepository.findByGroupGroupId(TEST_GROUP_ID))
                .thenReturn(Optional.of(testContract));
        when(depositCalculationService.calculateRequiredDepositAmount(testGroup))
                .thenReturn(newCalculatedAmount);
        when(contractRepository.save(any(Contract.class))).thenReturn(testContract);

        // When
        Contract result = contractService.recalculateRequiredDepositAmount(TEST_GROUP_ID);

        // Then
        assertNotNull(result);
        verify(groupRepository).findById(TEST_GROUP_ID);
        verify(contractRepository).findByGroupGroupId(TEST_GROUP_ID);
        verify(depositCalculationService).calculateRequiredDepositAmount(testGroup);
        verify(contractRepository).save(any(Contract.class));
    }

    @Test
    void cancelContract_Success() {
        // Given
        when(contractRepository.findByGroupGroupId(TEST_GROUP_ID))
                .thenReturn(Optional.of(testContract));
        when(contractRepository.save(any(Contract.class))).thenReturn(testContract);

        // When
        contractService.cancelContract(TEST_GROUP_ID);

        // Then
        verify(contractRepository).findByGroupGroupId(TEST_GROUP_ID);
        verify(contractRepository).save(any(Contract.class));
    }

    @Test
    void getContractInfo_Success() {
        // Given
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

        verify(contractRepository).findByGroupGroupId(TEST_GROUP_ID);
    }

    @Test
    void signContract_Success() {
        // Given
        Map<String, Object> signRequest = new HashMap<>();
        signRequest.put("signer", "admin@test.com");

        when(contractRepository.findByGroupGroupId(TEST_GROUP_ID))
                .thenReturn(Optional.of(testContract));
        when(contractRepository.save(any(Contract.class))).thenReturn(testContract);

        // When
        Map<String, Object> result = contractService.signContract(TEST_GROUP_ID, signRequest);

        // Then
        assertNotNull(result);
        assertTrue((Boolean) result.get("success"));
        assertEquals(TEST_CONTRACT_ID, result.get("contractId"));
        assertEquals("Contract signed successfully", result.get("message"));
        assertNotNull(result.get("signedAt"));

        verify(contractRepository).findByGroupGroupId(TEST_GROUP_ID);
        verify(contractRepository).save(any(Contract.class));
    }

    @Test
    void signContract_ContractNotActive() {
        // Given
        testContract.setIsActive(false);
        Map<String, Object> signRequest = new HashMap<>();
        signRequest.put("signer", "admin@test.com");

        when(contractRepository.findByGroupGroupId(TEST_GROUP_ID))
                .thenReturn(Optional.of(testContract));

        // When
        Map<String, Object> result = contractService.signContract(TEST_GROUP_ID, signRequest);

        // Then
        assertNotNull(result);
        assertFalse((Boolean) result.get("success"));
        assertEquals("Contract is not active", result.get("message"));

        verify(contractRepository).findByGroupGroupId(TEST_GROUP_ID);
        verify(contractRepository, never()).save(any(Contract.class));
    }
}
