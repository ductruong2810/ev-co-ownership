package com.group8.evcoownership.integration;

import com.group8.evcoownership.entity.*;
import com.group8.evcoownership.repository.*;
import com.group8.evcoownership.service.ContractService;
import com.group8.evcoownership.service.DepositCalculationService;
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

import java.math.BigDecimal;
import java.util.HashMap;
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

    @Test
    @Order(1)
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
        Contract result = contractService.createDefaultContract(testGroup.getGroupId());

        // Then
        assertNotNull(result);
        assertEquals(testGroup.getGroupId(), result.getGroup().getGroupId());
        assertNotNull(result.getTerms());
        assertTrue(result.getTerms().contains("contract") || result.getTerms().contains("Contract"));
    }

    @Test
    @Order(2)
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

        // Create contract first
        Contract contract = contractService.createDefaultContract(testGroup.getGroupId());
        assertNotNull(contract);

        // When
        var result = contractService.saveContractFromData(testGroup.getGroupId());

        // Then
        assertNotNull(result);
        assertNotNull(result.get("contractId"));
        assertNotNull(result.get("status"));
    }

    @Test
    @Order(3)
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

        // Step 1: Create contract
        Contract contract = contractService.createDefaultContract(testGroup.getGroupId());
        assertNotNull(contract);

        // Step 2: Save contract with data
        var generationResult = contractService.saveContractFromData(testGroup.getGroupId());
        assertNotNull(generationResult);

        // Step 3: Sign contract
        Map<String, Object> signRequest = Map.of(
                "terms", generationResult.get("terms"),
                "startDate", generationResult.get("startDate").toString(),
                "endDate", generationResult.get("endDate").toString(),
                "adminName", "Test Admin",
                "signatureType", "ADMIN_PROXY"
        );
        var signResult = contractService.signContractWithData(testGroup.getGroupId(), signRequest);
        assertTrue((Boolean) signResult.get("success"));

        // Verify final state
        Contract finalContract = contractRepository.findByGroupGroupId(testGroup.getGroupId()).orElse(null);
        assertNotNull(finalContract);
        assertTrue(finalContract.getTerms().contains("[ĐÃ KÝ]"));
    }
}