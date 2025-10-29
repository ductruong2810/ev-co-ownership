package com.group8.evcoownership.controller;

import com.group8.evcoownership.service.ContractService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Test cho ContractController để kiểm tra fix NullPointerException
 */
@ExtendWith(MockitoExtension.class)
class ContractControllerTest {

    @Mock
    private ContractService contractService;

    @InjectMocks
    private ContractController contractController;

    @Test
    void testGenerateContractData_WithValidEmail_ShouldReturnContractData() {
        // Arrange
        Long groupId = 1L;
        String userEmail = "test@example.com";
        Long userId = 123L;

        Map<String, Object> expectedContractData = new HashMap<>();
        expectedContractData.put("contractId", 1L);
        expectedContractData.put("groupId", groupId);
        expectedContractData.put("status", "DRAFT");

        when(contractService.getUserIdByEmail(userEmail)).thenReturn(userId);
        when(contractService.generateContractData(groupId, userId)).thenReturn(expectedContractData);

        // Act
        ResponseEntity<Map<String, Object>> response = contractController.generateContractData(groupId, userEmail);

        // Assert
        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(expectedContractData, response.getBody());

        verify(contractService).getUserIdByEmail(userEmail);
        verify(contractService).generateContractData(groupId, userId);
    }

    @Test
    void testGenerateContractData_WithNullEmail_ShouldThrowException() {
        // Arrange
        Long groupId = 1L;
        String userEmail = null;

        when(contractService.getUserIdByEmail(userEmail))
                .thenThrow(new IllegalArgumentException("Email cannot be null"));

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            contractController.generateContractData(groupId, userEmail);
        });

        verify(contractService).getUserIdByEmail(userEmail);
        verify(contractService, never()).generateContractData(any(), any());
    }

    @Test
    void testGenerateContractData_WithInvalidEmail_ShouldThrowException() {
        // Arrange
        Long groupId = 1L;
        String userEmail = "nonexistent@example.com";

        when(contractService.getUserIdByEmail(userEmail))
                .thenThrow(new jakarta.persistence.EntityNotFoundException("User not found with email: " + userEmail));

        // Act & Assert
        assertThrows(jakarta.persistence.EntityNotFoundException.class, () -> {
            contractController.generateContractData(groupId, userEmail);
        });

        verify(contractService).getUserIdByEmail(userEmail);
        verify(contractService, never()).generateContractData(any(), any());
    }
}