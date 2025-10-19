package com.group8.evcoownership.integration;

import com.group8.evcoownership.dto.ContractGenerationRequest;
import com.group8.evcoownership.entity.Contract;
import com.group8.evcoownership.entity.OwnershipGroup;
import com.group8.evcoownership.entity.OwnershipShare;
import com.group8.evcoownership.entity.User;
import com.group8.evcoownership.entity.Vehicle;
import com.group8.evcoownership.repository.ContractRepository;
import com.group8.evcoownership.repository.OwnershipGroupRepository;
import com.group8.evcoownership.repository.OwnershipShareRepository;
import com.group8.evcoownership.repository.UserRepository;
import com.group8.evcoownership.repository.VehicleRepository;
import com.group8.evcoownership.service.ContractGenerationService;
import com.group8.evcoownership.service.ContractService;
import com.group8.evcoownership.service.DepositCalculationService;
import com.group8.evcoownership.service.TemplateService;
import com.group8.evcoownership.testdata.ContractTestDataBuilder;
import com.group8.evcoownership.testconfig.TestConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestConfig.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ContractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

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

    @MockitoBean
    private TemplateService templateService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @Order(1)
    @WithMockUser(username = "admin@test.com")
    void contractWorkflow_EndToEnd_Success() throws Exception {
        // Create test data for this test only with unique IDs
        OwnershipGroup testGroup = ContractTestDataBuilder.TestScenarios.createBasicGroup();
        testGroup.setGroupId(null); // Let Hibernate generate ID
        testGroup = groupRepository.save(testGroup);

        User testUser = ContractTestDataBuilder.TestScenarios.createBasicUser();
        testUser.setUserId(null); // Let Hibernate generate ID
        testUser = userRepository.save(testUser);

        Vehicle testVehicle = ContractTestDataBuilder.TestScenarios.createBasicVehicle(testGroup);
        testVehicle.setId(null); // Let Hibernate generate ID
        testVehicle = vehicleRepository.save(testVehicle);

        OwnershipShare testShare = ContractTestDataBuilder.TestScenarios.createBasicShare(testGroup, testUser);
        testShare = shareRepository.save(testShare);

        Contract testContract = ContractTestDataBuilder.TestScenarios.createBasicContract(testGroup);
        testContract.setId(null); // Let Hibernate generate ID
        testContract = contractRepository.save(testContract);

        // Mock external services
        when(depositCalculationService.calculateRequiredDepositAmount(any(OwnershipGroup.class)))
                .thenReturn(new BigDecimal("2000000"));
        when(templateService.getTemplateContent())
                .thenReturn("<html><body>Contract {{data.contract.number}} for {{data.group.name}}</body></html>");

        // Step 1: Generate contract
        ContractGenerationRequest request = new ContractGenerationRequest(
                LocalDate.of(2025, 1, 1),
                LocalDate.of(2025, 12, 31),
                "Integration test contract terms",
                "Hà Nội",
                "2025-01-01",
                "EVS-INT-001"
        );

        mockMvc.perform(post("/api/contracts/generate/{groupId}", testGroup.getGroupId())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contractId").exists())
                .andExpect(jsonPath("$.contractNumber").exists())
                .andExpect(jsonPath("$.htmlContent").exists())
                .andExpect(jsonPath("$.status").value("GENERATED"));

        // Step 2: Preview contract
        mockMvc.perform(get("/api/contracts/preview/{groupId}", testGroup.getGroupId()))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.TEXT_HTML_VALUE))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Contract")));

        // Step 3: Get contract info
        mockMvc.perform(get("/api/contracts/{groupId}", testGroup.getGroupId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contractId").exists())
                .andExpect(jsonPath("$.groupId").value(testGroup.getGroupId()))
                .andExpect(jsonPath("$.groupName").value(testGroup.getGroupName()))
                .andExpect(jsonPath("$.isActive").value(true));

        // Step 4: Sign contract
        Map<String, Object> signRequest = Map.of(
                "signer", "admin@test.com",
                "signature", "digital_signature"
        );

        mockMvc.perform(post("/api/contracts/{groupId}/sign", testGroup.getGroupId())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(signRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Contract signed successfully"));

        // Step 5: Export contract to PDF
        mockMvc.perform(get("/api/contracts/export/{groupId}/pdf", testGroup.getGroupId()))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().exists("Content-Disposition"));
    }

    @Test
    @Order(2)
    @WithMockUser(username = "admin@test.com")
    void contractService_CreateDefaultContract_Success() throws Exception {
        // Create test data with unique ID
        OwnershipGroup testGroup = ContractTestDataBuilder.TestScenarios.createBasicGroup();
        testGroup.setGroupId(null); // Let Hibernate generate ID
        testGroup = groupRepository.save(testGroup);

        // Test service method directly
        Contract contract = contractService.createDefaultContract(testGroup.getGroupId());
        
        assert contract != null;
        assert contract.getGroup().getGroupId().equals(testGroup.getGroupId());
        assert contract.getTerms().contains("Standard EV co-ownership contract");
    }

    @Test
    @Order(3)
    @WithMockUser(username = "admin@test.com")
    void contractGenerationService_GenerateContract_Success() throws Exception {
        // Create test data with unique ID
        OwnershipGroup testGroup = ContractTestDataBuilder.TestScenarios.createBasicGroup();
        testGroup.setGroupId(null); // Let Hibernate generate ID
        testGroup = groupRepository.save(testGroup);

        // Create a default contract first
        Contract defaultContract = contractService.createDefaultContract(testGroup.getGroupId());

        // Mock external services
        when(depositCalculationService.calculateRequiredDepositAmount(any(OwnershipGroup.class)))
                .thenReturn(new BigDecimal("2000000"));
        when(templateService.getTemplateContent())
                .thenReturn("<html><body>Contract {{data.contract.number}} for {{data.group.name}}</body></html>");

        // Test service method directly
        ContractGenerationRequest request = new ContractGenerationRequest(
                LocalDate.of(2025, 1, 1),
                LocalDate.of(2025, 12, 31),
                "Test contract terms",
                "Hà Nội",
                "2025-01-01",
                "EVS-TEST-001"
        );

        var result = contractGenerationService.generateContract(testGroup.getGroupId(), request);
        
        assert result != null;
        assert result.contractId() != null;
        assert result.status().equals("GENERATED");
    }
}
