package com.group8.evcoownership.integration;

import com.group8.evcoownership.entity.*;
import com.group8.evcoownership.repository.*;
import com.group8.evcoownership.service.ContractService;
import com.group8.evcoownership.service.DepositCalculationService;
import com.group8.evcoownership.service.NotificationOrchestrator;
import com.group8.evcoownership.testconfig.TestConfig;
import com.group8.evcoownership.testdata.ContractTestDataBuilder;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(TestConfig.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ContractIntegrationTest {

    @Autowired
    private ContractService contractService;

    @Autowired
    private ContractRepository contractRepository;

    @Autowired
    private OwnershipGroupRepository groupRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private VehicleRepository vehicleRepository;

    @Autowired
    private OwnershipShareRepository shareRepository;

    @MockitoBean
    private DepositCalculationService depositCalculationService;

    @MockitoBean
    private NotificationOrchestrator notificationOrchestrator;

    @Test
    @Order(1)
    @Transactional
    void contractService_CreateDefaultContract_Success() {
        // Given
        OwnershipGroup testGroup = ContractTestDataBuilder.TestScenarios.createBasicGroup();
        testGroup.setGroupId(null);
        testGroup = groupRepository.save(testGroup);

        // Create enough members to match memberCapacity (5)
        for (int i = 1; i <= 5; i++) {
            User testUser = ContractTestDataBuilder.TestScenarios.createBasicUser();
            testUser.setUserId(null);
            testUser.setEmail("user" + i + "@test.com");
            testUser = userRepository.save(testUser);

            OwnershipShare testShare = ContractTestDataBuilder.TestScenarios.createBasicShare(testGroup, testUser);
            shareRepository.save(testShare);
        }

        // When
        Map<String, Object> contractData = contractService.generateContractData(testGroup.getGroupId(), 1L);

        // Then
        assertNotNull(contractData);
        assertEquals(testGroup.getGroupId(), contractData.get("groupId"));
        assertNotNull(contractData.get("terms"));
        assertTrue(((String) contractData.get("terms")).contains("HỢP ĐỒNG") || ((String) contractData.get("terms")).contains("hợp đồng"));
    }

    @Test
    @Order(2)
    @Transactional
    void contractGenerationService_GenerateContractAuto_Success() {
        // Given
        OwnershipGroup testGroup = ContractTestDataBuilder.TestScenarios.createBasicGroup();
        testGroup.setGroupId(null);
        testGroup = groupRepository.save(testGroup);

        // Create enough members to match memberCapacity (5)
        for (int i = 1; i <= 5; i++) {
            User testUser = ContractTestDataBuilder.TestScenarios.createBasicUser();
            testUser.setUserId(null);
            testUser.setEmail("user" + i + "@test.com");
            testUser = userRepository.save(testUser);

            OwnershipShare testShare = ContractTestDataBuilder.TestScenarios.createBasicShare(testGroup, testUser);
            shareRepository.save(testShare);
        }

        // Generate contract data first
        Map<String, Object> contractData = contractService.generateContractData(testGroup.getGroupId(), 1L);
        assertNotNull(contractData);

        // When - Auto sign contract
        var result = contractService.autoSignContract(testGroup.getGroupId());

        // Then
        assertNotNull(result);
        assertNotNull(result.get("contractId"));
        assertNotNull(result.get("status"));
    }

    @Test
    @Order(3)
    @Transactional
    void contractWorkflow_EndToEnd_Success() {
        // Given
        OwnershipGroup testGroup = ContractTestDataBuilder.TestScenarios.createBasicGroup();
        testGroup.setGroupId(null);
        testGroup = groupRepository.save(testGroup);

        // Create enough members to match memberCapacity (5)
        for (int i = 1; i <= 5; i++) {
            User testUser = ContractTestDataBuilder.TestScenarios.createBasicUser();
            testUser.setUserId(null);
            testUser.setEmail("user" + i + "@test.com");
            testUser = userRepository.save(testUser);

            OwnershipShare testShare = ContractTestDataBuilder.TestScenarios.createBasicShare(testGroup, testUser);
            shareRepository.save(testShare);
        }

        Vehicle testVehicle = ContractTestDataBuilder.TestScenarios.createBasicVehicle(testGroup);
        testVehicle.setId(null);
        vehicleRepository.save(testVehicle);

        // Mock deposit calculation
        when(depositCalculationService.calculateRequiredDepositAmount(any(OwnershipGroup.class)))
                .thenReturn(new BigDecimal("2000000"));

        // Step 1: Generate contract data
        Map<String, Object> contractData = contractService.generateContractData(testGroup.getGroupId(), 1L);
        assertNotNull(contractData);

        // Step 2: Auto sign contract
        var signResult = contractService.autoSignContract(testGroup.getGroupId());
        assertTrue((Boolean) signResult.get("success"));

        // Verify final state
        Contract finalContract = contractRepository.findByGroupGroupId(testGroup.getGroupId()).orElse(null);
        assertNotNull(finalContract);
        assertTrue(finalContract.getTerms().contains("[ĐÃ KÝ TỰ ĐỘNG]"));
    }
}