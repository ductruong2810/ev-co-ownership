package com.group8.evcoownership.controller;

import com.group8.evcoownership.entity.User;
import com.group8.evcoownership.exception.InvalidContractActionException;
import com.group8.evcoownership.exception.ResourceNotFoundException;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import com.group8.evcoownership.dto.ContractApprovalRequestDTO;
import com.group8.evcoownership.dto.ContractDTO;
import com.group8.evcoownership.enums.ContractApprovalStatus;
import com.group8.evcoownership.service.ContractService;
import com.group8.evcoownership.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/contracts")
@RequiredArgsConstructor
public class AdminContractController {

    private final ContractService contractService;

//    @PutMapping("/{contractId}/approve")
//    @PreAuthorize("hasRole('ADMIN')")
//    public ResponseEntity<?> approveContract(
//            @PathVariable Long contractId,
//            @AuthenticationPrincipal UserDetails userDetails) {
//
//        User admin = userService.findByEmail(userDetails.getUsername());
//        ContractDTO approvedContract = contractService.approveContract(contractId, admin);
//        return ResponseEntity.ok(approvedContract);
//    }
//
//    @PutMapping("/{contractId}/reject")
//    @PreAuthorize("hasRole('ADMIN')")
//    public ResponseEntity<?> rejectContract(
//            @PathVariable Long contractId,
//            @RequestParam String reason,
//            @AuthenticationPrincipal UserDetails userDetails) {
//
//        User admin = userService.findByEmail(userDetails.getUsername());
//        ContractDTO rejectedContract = contractService.rejectContract(contractId, reason, admin);
//        return ResponseEntity.ok(rejectedContract);
//    }

    @PutMapping("/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ContractDTO> processContractApproval(
            @Valid @RequestBody ContractApprovalRequestDTO request,
            @AuthenticationPrincipal UserDetails userDetails) {

        User admin = (User) userDetails;

        // Validate contract exists
        if (!contractService.contractExists(request.getContractId())) {
            throw new ResourceNotFoundException("Contract not found with ID: " + request.getContractId());
        }

        ContractDTO contract;

        if ("APPROVE".equalsIgnoreCase(request.getAction())) {
            contract = contractService.approveContract(request.getContractId(), admin);

        } else if ("REJECT".equalsIgnoreCase(request.getAction())) {
            // Validate reason for rejection
            if (request.getReason() == null || request.getReason().isBlank()) {
                throw new InvalidContractActionException("Rejection reason is required when rejecting a contract");
            }

            if (request.getReason().trim().length() < 10) {
                throw new InvalidContractActionException("Rejection reason must be at least 10 characters");
            }

            contract = contractService.rejectContract(
                    request.getContractId(),
                    request.getReason().trim(),
                    admin
            );

        } else {
            throw new InvalidContractActionException(
                    "Invalid action. Only 'APPROVE' or 'REJECT' are allowed"
            );
        }

        return ResponseEntity.ok(contract);
    }

    /**
     * Kiểm tra trạng thái đóng tiền cọc của hợp đồng (Admin only)
     */
    @GetMapping("/{groupId}/deposit-status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> checkDepositStatus(@PathVariable Long groupId) {
        Map<String, Object> status = contractService.checkDepositStatus(groupId);
        return ResponseEntity.ok(status);
    }


    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<ContractDTO>> getAllContracts() {
        List<ContractDTO> contracts = contractService.getAllContracts();
        return ResponseEntity.ok(contracts);
    }

    @GetMapping("/{contractId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ContractDTO> getContractById(@PathVariable Long contractId) {
        ContractDTO contract = contractService.getContractById(contractId);
        return ResponseEntity.ok(contract);
    }

    @GetMapping("/pending")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<ContractDTO>> getPendingContracts() {
        List<ContractDTO> contracts = contractService.getContractsByStatus(ContractApprovalStatus.PENDING);
        return ResponseEntity.ok(contracts);
    }
}
