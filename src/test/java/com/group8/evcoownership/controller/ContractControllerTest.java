package com.group8.evcoownership.controller;

import com.group8.evcoownership.dto.ContractGenerationResponseDTO;
import com.group8.evcoownership.service.ContractService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

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

        ContractGenerationResponseDTO expectedContractData = ContractGenerationResponseDTO.builder()
                .contractId(1L)
                .groupId(groupId)
                .status(null)
                .build();

        when(contractService.getUserIdByEmail(userEmail)).thenReturn(userId);
        when(contractService.generateContractData(groupId, userId)).thenReturn(expectedContractData);

        // Act
        ResponseEntity<ContractGenerationResponseDTO> response = contractController.generateContractData(groupId, userEmail);

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

        when(contractService.getUserIdByEmail(null))
                .thenThrow(new IllegalArgumentException("Email cannot be null"));

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> contractController.generateContractData(groupId, null));

        verify(contractService).getUserIdByEmail(null);
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
        assertThrows(jakarta.persistence.EntityNotFoundException.class, () -> contractController.generateContractData(groupId, userEmail));

        verify(contractService).getUserIdByEmail(userEmail);
        verify(contractService, never()).generateContractData(any(), any());
    }
}