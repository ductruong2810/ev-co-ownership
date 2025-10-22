package com.group8.evcoownership.service;

import com.group8.evcoownership.dto.ContractGenerationRequest;
import com.group8.evcoownership.dto.ContractGenerationResponse;
import com.group8.evcoownership.entity.*;
import com.group8.evcoownership.repository.ContractRepository;
import com.group8.evcoownership.repository.OwnershipGroupRepository;
import com.group8.evcoownership.repository.OwnershipShareRepository;
import com.group8.evcoownership.repository.VehicleRepository;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ContractGenerationServiceTest {

    @Mock
    private ContractService contractService;

    @Mock
    private ContractRepository contractRepository;

    @Mock
    private OwnershipGroupRepository groupRepository;

    @Mock
    private VehicleRepository vehicleRepository;

    @Mock
    private OwnershipShareRepository shareRepository;

    // TemplateService đã được xóa, template sẽ được xử lý ở Frontend
    // private TemplateService templateService;

    @InjectMocks
    private ContractGenerationService contractGenerationService;

    private OwnershipGroup testGroup;
    private Contract testContract;
    private Vehicle testVehicle;
    private User testUser;
    private OwnershipShare testShare;
    private ContractGenerationRequest testRequest;
    private final Long TEST_GROUP_ID = 1L;
    private final Long TEST_CONTRACT_ID = 1L;
    private final Long TEST_VEHICLE_ID = 1L;
    private final Long TEST_USER_ID = 1L;

    @BeforeEach
    void setUp() {
        // Setup test group
        testGroup = OwnershipGroup.builder()
                .groupId(TEST_GROUP_ID)
                .groupName("Test EV Group")
                .description("Test group for EV co-ownership")
                .memberCapacity(5)
                .build();

        // Setup test user
        testUser = User.builder()
                .userId(TEST_USER_ID)
                .fullName("Test User")
                .email("test@example.com")
                .phoneNumber("0123456789")
                .build();

        // Setup test vehicle
        testVehicle = Vehicle.builder()
                .Id(TEST_VEHICLE_ID)
                .brand("VinFast")
                .model("VF 8 Plus")
                .licensePlate("30A-123.45")
                .chassisNumber("RLVZZZ1EZBW000001")
                .vehicleValue(new BigDecimal("950000000"))
                .ownershipGroup(testGroup)
                .build();

        // Setup test share
        testShare = OwnershipShare.builder()
                .id(new OwnershipShareId(TEST_USER_ID, TEST_GROUP_ID))
                .group(testGroup)
                .user(testUser)
                .ownershipPercentage(new BigDecimal("20.00"))
                .build();

        // Setup test contract
        testContract = Contract.builder()
                .id(TEST_CONTRACT_ID)
                .group(testGroup)
                .startDate(LocalDate.of(2025, 1, 1))
                .endDate(LocalDate.of(2025, 12, 31))
                .terms("Test contract terms")
                .requiredDepositAmount(new BigDecimal("2000000"))
                .isActive(true)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        // Setup test request
        testRequest = new ContractGenerationRequest(
                LocalDate.of(2025, 1, 1),
                LocalDate.of(2025, 12, 31),
                "Test contract terms",
                "Hà Nội",
                "2025-01-01",
                "EVS-001"
        );
    }

    @Test
    void generateContractAuto_NewContract_Success() {
        // Given
        when(groupRepository.findById(TEST_GROUP_ID)).thenReturn(Optional.of(testGroup));
        when(contractRepository.findByGroupGroupId(TEST_GROUP_ID)).thenReturn(Optional.empty());
        when(contractService.getRequiredDepositAmount(TEST_GROUP_ID)).thenReturn(new BigDecimal("2000000"));
        when(contractRepository.save(any(Contract.class))).thenReturn(testContract);
        // Template sẽ được truyền từ Frontend
        when(vehicleRepository.findByOwnershipGroup(testGroup)).thenReturn(Optional.of(testVehicle));
        when(shareRepository.findByGroupGroupId(TEST_GROUP_ID)).thenReturn(List.of(testShare));

        String tsxTemplate = "<Component>{{data.contract.number}}</Component>";

        // When
        ContractGenerationResponse result = contractGenerationService.generateContractAuto(TEST_GROUP_ID, "REACT_TSX", tsxTemplate);

        // Then
        assertNotNull(result);
        assertEquals(TEST_CONTRACT_ID, result.contractId());
        assertNotNull(result.contractNumber());
        assertTrue(result.contractNumber().startsWith("EVS-"));
        assertNotNull(result.props());
        assertEquals("GENERATED", result.status());
        assertNotNull(result.generatedAt());

        verify(groupRepository, times(2)).findById(TEST_GROUP_ID);
        verify(contractRepository).findByGroupGroupId(TEST_GROUP_ID);
        verify(contractService).getRequiredDepositAmount(TEST_GROUP_ID);
        verify(contractRepository).save(any(Contract.class));
        // TemplateService đã được xóa
    }

    @Test
    void generateContractAuto_UpdateExistingContract_Success() {
        // Given
        when(groupRepository.findById(TEST_GROUP_ID)).thenReturn(Optional.of(testGroup));
        when(contractRepository.findByGroupGroupId(TEST_GROUP_ID)).thenReturn(Optional.of(testContract));
        when(contractRepository.save(any(Contract.class))).thenReturn(testContract);
        // Template sẽ được truyền từ Frontend
        when(vehicleRepository.findByOwnershipGroup(testGroup)).thenReturn(Optional.of(testVehicle));
        when(shareRepository.findByGroupGroupId(TEST_GROUP_ID)).thenReturn(List.of(testShare));

        String tsxTemplate = "<Component>{{data.contract.number}}</Component>";

        // When
        ContractGenerationResponse result = contractGenerationService.generateContractAuto(TEST_GROUP_ID, "REACT_TSX", tsxTemplate);

        // Then
        assertNotNull(result);
        assertEquals(TEST_CONTRACT_ID, result.contractId());
        assertNotNull(result.contractNumber());
        assertNotNull(result.props());
        assertEquals("GENERATED", result.status());

        verify(groupRepository, times(2)).findById(TEST_GROUP_ID);
        verify(contractRepository).findByGroupGroupId(TEST_GROUP_ID);
        verify(contractRepository).save(any(Contract.class));
        verify(contractService, never()).getRequiredDepositAmount(anyLong());
    }

    @Test
    void generateContractAuto_GroupNotFound() {
        // Given
        when(groupRepository.findById(TEST_GROUP_ID)).thenReturn(Optional.empty());
        String tsxTemplate = "<Component>{{data.contract.number}}</Component>";

        // When & Then
        assertThrows(EntityNotFoundException.class, () ->
                contractGenerationService.generateContractAuto(TEST_GROUP_ID, "REACT_TSX", tsxTemplate));

        verify(groupRepository).findById(TEST_GROUP_ID);
    }

    // Removed HTML preview tests (TSX-only)
    @Test
    void exportToPdf_Success() {
        // Given
        String content = "<Component>Test Contract</Component>";
        when(contractRepository.findByGroupGroupId(TEST_GROUP_ID)).thenReturn(Optional.of(testContract));
        // Template sẽ được truyền từ Frontend
        when(vehicleRepository.findByOwnershipGroup(testGroup)).thenReturn(Optional.of(testVehicle));
        when(shareRepository.findByGroupGroupId(TEST_GROUP_ID)).thenReturn(List.of(testShare));

        String tsxTemplate = content;

        // When
        byte[] result = contractGenerationService.exportToPdf(TEST_GROUP_ID, tsxTemplate);

        // Then
        assertNotNull(result);
        assertEquals(content, new String(result));

        verify(contractRepository).findByGroupGroupId(TEST_GROUP_ID);
        // TemplateService đã được xóa
    }

    @Test
    void prepareContractData_WithVehicle_Success() {
        // Given
        when(vehicleRepository.findByOwnershipGroup(testGroup)).thenReturn(Optional.of(testVehicle));
        when(shareRepository.findByGroupGroupId(TEST_GROUP_ID)).thenReturn(List.of(testShare));

        // When - Using reflection to test private method
        try {
            var method = ContractGenerationService.class.getDeclaredMethod("prepareContractData", Long.class, Contract.class);
            method.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<String, Object> result = (Map<String, Object>) method.invoke(contractGenerationService, TEST_GROUP_ID, testContract);

            // Then
            assertNotNull(result);
            assertTrue(result.containsKey("contract"));
            assertTrue(result.containsKey("group"));
            assertTrue(result.containsKey("vehicle"));
            assertTrue(result.containsKey("finance"));
            assertTrue(result.containsKey("usage"));
            assertTrue(result.containsKey("maintenance"));
            assertTrue(result.containsKey("dispute"));
            assertTrue(result.containsKey("owners"));

            @SuppressWarnings("unchecked")
            Map<String, Object> contractInfo = (Map<String, Object>) result.get("contract");
            assertNotNull(contractInfo.get("number"));
            assertEquals("01/01/2025", contractInfo.get("effectiveDate"));
            assertEquals("31/12/2025", contractInfo.get("endDate"));
            assertEquals("11 tháng", contractInfo.get("termLabel"));

            @SuppressWarnings("unchecked")
            Map<String, Object> vehicleInfo = (Map<String, Object>) result.get("vehicle");
            assertEquals("VinFast VF 8 Plus", vehicleInfo.get("model"));
            assertEquals("30A-123.45", vehicleInfo.get("plate"));
            assertEquals("RLVZZZ1EZBW000001", vehicleInfo.get("vin"));

            @SuppressWarnings("unchecked")
            Map<String, Object> financeInfo = (Map<String, Object>) result.get("finance");
            assertEquals(new BigDecimal("950000000"), financeInfo.get("vehiclePrice"));
            assertEquals(new BigDecimal("2000000"), financeInfo.get("depositAmount"));

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> owners = (List<Map<String, Object>>) result.get("owners");
            assertEquals(1, owners.size());
            assertEquals("Test User", owners.get(0).get("name"));
            assertEquals("test@example.com", owners.get(0).get("email"));
            assertEquals("0123456789", owners.get(0).get("phone"));

        } catch (Exception e) {
            fail("Failed to test private method: " + e.getMessage());
        }
    }

    @Test
    void prepareContractData_WithoutVehicle_Success() {
        // Given
        when(vehicleRepository.findByOwnershipGroup(testGroup)).thenReturn(Optional.empty());
        when(shareRepository.findByGroupGroupId(TEST_GROUP_ID)).thenReturn(List.of(testShare));

        // When - Using reflection to test private method
        try {
            var method = ContractGenerationService.class.getDeclaredMethod("prepareContractData", Long.class, Contract.class);
            method.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<String, Object> result = (Map<String, Object>) method.invoke(contractGenerationService, TEST_GROUP_ID, testContract);

            // Then
            @SuppressWarnings("unchecked")
            Map<String, Object> vehicleInfo = (Map<String, Object>) result.get("vehicle");
            assertEquals("—", vehicleInfo.get("model"));
            assertEquals("—", vehicleInfo.get("plate"));
            assertEquals("—", vehicleInfo.get("vin"));

            @SuppressWarnings("unchecked")
            Map<String, Object> financeInfo = (Map<String, Object>) result.get("finance");
            assertEquals(new BigDecimal("0"), financeInfo.get("vehiclePrice"));

        } catch (Exception e) {
            fail("Failed to test private method: " + e.getMessage());
        }
    }

    @Test
    void replaceTemplatePlaceholders_Success() {
        // Given
        String template = "Contract {{data.contract.number}} for {{data.group.name}}";
        Map<String, Object> data = Map.of(
                "contract", Map.of("number", "EVS-001"),
                "group", Map.of("name", "Test Group")
        );

        // When - Using reflection to test private method
        try {
            var method = ContractGenerationService.class.getDeclaredMethod("replaceTemplatePlaceholders", String.class, Map.class);
            method.setAccessible(true);
            String result = (String) method.invoke(contractGenerationService, template, data);

            // Then
            assertEquals("Contract EVS-001 for Test Group", result);

        } catch (Exception e) {
            fail("Failed to test private method: " + e.getMessage());
        }
    }

    @Test
    void formatCurrency_Success() {
        // Given
        BigDecimal amount = new BigDecimal("2000000");

        // When - Using reflection to test private method
        try {
            var method = ContractGenerationService.class.getDeclaredMethod("formatCurrency", BigDecimal.class);
            method.setAccessible(true);
            String result = (String) method.invoke(contractGenerationService, amount);

            // Then
            assertEquals("2,000,000 VND", result);

        } catch (Exception e) {
            fail("Failed to test private method: " + e.getMessage());
        }
    }

    @Test
    void formatDate_Success() {
        // Given
        LocalDate date = LocalDate.of(2025, 1, 1);

        // When - Using reflection to test private method
        try {
            var method = ContractGenerationService.class.getDeclaredMethod("formatDate", LocalDate.class);
            method.setAccessible(true);
            String result = (String) method.invoke(contractGenerationService, date);

            // Then
            assertEquals("01/01/2025", result);

        } catch (Exception e) {
            fail("Failed to test private method: " + e.getMessage());
        }
    }

    @Test
    void calculateTermLabel_Success() {
        // Given
        LocalDate startDate = LocalDate.of(2025, 1, 1);
        LocalDate endDate = LocalDate.of(2025, 12, 31);

        // When - Using reflection to test private method
        try {
            var method = ContractGenerationService.class.getDeclaredMethod("calculateTermLabel", LocalDate.class, LocalDate.class);
            method.setAccessible(true);
            String result = (String) method.invoke(contractGenerationService, startDate, endDate);

            // Then
            assertEquals("11 tháng", result);

        } catch (Exception e) {
            fail("Failed to test private method: " + e.getMessage());
        }
    }

    @Test
    void generateContractNumber_Success() {
        // Given
        Long contractId = 123L;

        // When - Using reflection to test private method
        try {
            var method = ContractGenerationService.class.getDeclaredMethod("generateContractNumber", Long.class);
            method.setAccessible(true);
            String result = (String) method.invoke(contractGenerationService, contractId);

            // Then
            assertTrue(result.startsWith("EVS-0123-"));
            assertTrue(result.contains(String.valueOf(LocalDateTime.now().getYear())));

        } catch (Exception e) {
            fail("Failed to test private method: " + e.getMessage());
        }
    }
}
