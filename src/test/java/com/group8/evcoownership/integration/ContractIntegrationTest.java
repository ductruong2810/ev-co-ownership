package com.group8.evcoownership.integration;

import com.group8.evcoownership.dto.SaveContractDataRequest;
import com.group8.evcoownership.entity.*;
import com.group8.evcoownership.repository.*;
import com.group8.evcoownership.service.ContractGenerationService;
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
    private ContractGenerationService contractGenerationService;

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

        // Create contract first
        Contract contract = contractService.createDefaultContract(testGroup.getGroupId());
        assertNotNull(contract);

        SaveContractDataRequest request = new SaveContractDataRequest("Test contract terms");

        // When
        var result = contractGenerationService.saveContractFromData(testGroup.getGroupId(), request);

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

        User testUser = ContractTestDataBuilder.TestScenarios.createBasicUser();
        testUser.setUserId(null);
        testUser = userRepository.save(testUser);

        Vehicle testVehicle = ContractTestDataBuilder.TestScenarios.createBasicVehicle(testGroup);
        testVehicle.setId(null);
        vehicleRepository.save(testVehicle);

        OwnershipShare testShare = ContractTestDataBuilder.TestScenarios.createBasicShare(testGroup, testUser);
        shareRepository.save(testShare);

        // Mock deposit calculation
        when(depositCalculationService.calculateRequiredDepositAmount(any(OwnershipGroup.class)))
                .thenReturn(new BigDecimal("2000000"));

        // Step 1: Create contract
        Contract contract = contractService.createDefaultContract(testGroup.getGroupId());
        assertNotNull(contract);

        // Step 2: Save contract with data
        SaveContractDataRequest request = new SaveContractDataRequest("Test contract terms");
        var generationResult = contractGenerationService.saveContractFromData(testGroup.getGroupId(), request);
        assertNotNull(generationResult);

        // Step 3: Sign contract
        Map<String, Object> signRequest = Map.of(
                "adminName", "Test Admin",
                "signatureType", "ADMIN_PROXY"
        );
        var signResult = contractService.signContract(testGroup.getGroupId(), signRequest);
        assertTrue((Boolean) signResult.get("success"));

        // Verify final state
        Contract finalContract = contractRepository.findByGroupGroupId(testGroup.getGroupId()).orElse(null);
        assertNotNull(finalContract);
        assertTrue(finalContract.getTerms().contains("[ĐÃ KÝ]"));
    }
}