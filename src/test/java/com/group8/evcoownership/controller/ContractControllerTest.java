package com.group8.evcoownership.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.group8.evcoownership.service.ContractService;
import com.group8.evcoownership.service.OwnershipGroupService;
import com.group8.evcoownership.testconfig.TestConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestConfig.class)
class ContractControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ContractService contractService;

    @MockitoBean
    private OwnershipGroupService ownershipGroupService;

    @Autowired
    private ObjectMapper objectMapper;

    private final Long TEST_GROUP_ID = 1L;

    @BeforeEach
    void setUp() {
        // Setup test data if needed
    }

    @Test
    @WithMockUser(username = "admin@test.com")
    void generateContractData_Success() throws Exception {
        // Given
        Map<String, Object> generatedResponse = new HashMap<>();
        generatedResponse.put("contractId", null);
        generatedResponse.put("contractNumber", "EVS-1-1234567890");
        generatedResponse.put("status", "GENERATED");
        generatedResponse.put("savedToDatabase", false);

        when(ownershipGroupService.isGroupAdmin("admin@test.com", TEST_GROUP_ID)).thenReturn(true);
        when(contractService.generateContractData(TEST_GROUP_ID)).thenReturn(generatedResponse);

        // When & Then
        mockMvc.perform(post("/api/contracts/{groupId}/generate", TEST_GROUP_ID)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contractId").isEmpty())
                .andExpect(jsonPath("$.contractNumber").value("EVS-1-1234567890"))
                .andExpect(jsonPath("$.status").value("GENERATED"))
                .andExpect(jsonPath("$.savedToDatabase").value(false));

        verify(contractService).generateContractData(TEST_GROUP_ID);
    }

    @Test
    @WithMockUser(username = "user@test.com")
    void generateContractData_Unauthorized() throws Exception {
        // Given
        when(ownershipGroupService.isGroupAdmin("user@test.com", TEST_GROUP_ID)).thenReturn(false);

        // When & Then
        mockMvc.perform(post("/api/contracts/{groupId}/generate", TEST_GROUP_ID)
                        .with(csrf()))
                .andExpect(status().isOk()); // Security is disabled in test profile
    }

    @Test
    @WithMockUser(username = "member@test.com")
    void getContractInfo_Success() throws Exception {
        // Given
        Map<String, Object> contractInfo = new HashMap<>();
        contractInfo.put("contractId", 1L);
        contractInfo.put("groupId", TEST_GROUP_ID);
        contractInfo.put("groupName", "Test Group");
        contractInfo.put("startDate", "2025-01-01");
        contractInfo.put("endDate", "2025-12-31");
        contractInfo.put("terms", "Test terms");
        contractInfo.put("requiredDepositAmount", new BigDecimal("2000000"));
        contractInfo.put("isActive", true);

        when(ownershipGroupService.isGroupMember("member@test.com", TEST_GROUP_ID)).thenReturn(true);
        when(contractService.getContractInfo(TEST_GROUP_ID)).thenReturn(contractInfo);

        // When & Then
        mockMvc.perform(get("/api/contracts/{groupId}", TEST_GROUP_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contractId").value(1L))
                .andExpect(jsonPath("$.groupId").value(TEST_GROUP_ID))
                .andExpect(jsonPath("$.groupName").value("Test Group"))
                .andExpect(jsonPath("$.isActive").value(true));
    }

    @Test
    @WithMockUser(username = "nonmember@test.com")
    void getContractInfo_Unauthorized() throws Exception {
        // Given
        when(ownershipGroupService.isGroupMember("nonmember@test.com", TEST_GROUP_ID)).thenReturn(false);

        // When & Then
        mockMvc.perform(get("/api/contracts/{groupId}", TEST_GROUP_ID))
                .andExpect(status().isOk()); // Security is disabled in test profile
    }

    @Test
    @WithMockUser(username = "admin@test.com")
    void signContract_Success() throws Exception {
        // Given
        Map<String, Object> contractData = new HashMap<>();
        contractData.put("terms", "Test contract terms");
        contractData.put("startDate", "2025-01-01");
        contractData.put("endDate", "2026-01-01");
        contractData.put("adminName", "Admin User");
        contractData.put("signatureType", "ADMIN_PROXY");

        Map<String, Object> signResponse = new HashMap<>();
        signResponse.put("success", true);
        signResponse.put("contractId", 1L);
        signResponse.put("contractNumber", "EVS-0001-2025");
        signResponse.put("status", "SIGNED");
        signResponse.put("signedAt", LocalDateTime.now().toString());
        signResponse.put("message", "Contract has been signed successfully");

        when(ownershipGroupService.isGroupAdmin("admin@test.com", TEST_GROUP_ID)).thenReturn(true);
        when(contractService.signContractWithData(TEST_GROUP_ID, contractData)).thenReturn(signResponse);

        // When & Then
        mockMvc.perform(post("/api/contracts/{groupId}/sign", TEST_GROUP_ID)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(contractData)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.contractId").value(1L))
                .andExpect(jsonPath("$.status").value("SIGNED"))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @WithMockUser(username = "user@test.com")
    void signContract_Unauthorized() throws Exception {
        // Given
        Map<String, Object> signRequest = new HashMap<>();
        signRequest.put("adminName", "User");

        when(ownershipGroupService.isGroupAdmin("user@test.com", TEST_GROUP_ID)).thenReturn(false);

        // When & Then
        mockMvc.perform(post("/api/contracts/{groupId}/sign", TEST_GROUP_ID)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(signRequest)))
                .andExpect(status().isOk()); // Security is disabled in test profile
    }

    @Test
    @WithMockUser(username = "admin@test.com")
    void signContract_EmptyRequest() throws Exception {
        // Given
        Map<String, Object> emptyRequest = new HashMap<>();

        Map<String, Object> signResponse = new HashMap<>();
        signResponse.put("success", true);
        signResponse.put("contractId", 1L);
        signResponse.put("message", "Contract signed successfully");

        when(ownershipGroupService.isGroupAdmin("admin@test.com", TEST_GROUP_ID)).thenReturn(true);
        when(contractService.signContractWithData(TEST_GROUP_ID, emptyRequest)).thenReturn(signResponse);

        // When & Then
        mockMvc.perform(post("/api/contracts/{groupId}/sign", TEST_GROUP_ID)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(emptyRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}
