package com.group8.evcoownership.controller;

import com.group8.evcoownership.service.ContractService;
import lombok.RequiredArgsConstructor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/contracts")
@RequiredArgsConstructor
@Tag(name = "Contracts", description = "Create, export PDF, sign and approve contracts")
public class ContractController {

    private final ContractService contractService;

    /**
     * Get contract info of group (for members)
     */
    @GetMapping("/{groupId}")
    @PreAuthorize("@ownershipGroupService.isGroupMember(authentication.name, #groupId)")
    @Operation(summary = "Get contract information", description = "Get contract information of the group")
    public ResponseEntity<Map<String, Object>> getContractInfo(@PathVariable Long groupId) {
        Map<String, Object> contractInfo = contractService.getContractInfo(groupId);
        return ResponseEntity.ok(contractInfo);
    }

    /**
     * Save contract data to database
     */
    @PostMapping("/{groupId}/save")
    @PreAuthorize("@ownershipGroupService.isGroupAdmin(authentication.name, #groupId)")
    @Operation(summary = "Save contract data", description = "Save contract data to database")
    public ResponseEntity<Map<String, Object>> saveContractData(@PathVariable Long groupId) {
        Map<String, Object> savedContract = contractService.saveContractFromData(groupId);
        return ResponseEntity.ok(savedContract);
    }

    /**
     * Sign contract (admin only)
     */
    @PostMapping("/{groupId}/sign")
    @PreAuthorize("@ownershipGroupService.isGroupAdmin(authentication.name, #groupId)")
    @Operation(summary = "Sign contract", description = "Only group admin can sign the contract")
    public ResponseEntity<Map<String, Object>> signContract(
            @PathVariable Long groupId,
            @RequestBody Map<String, Object> signRequest) {

        Map<String, Object> result = contractService.signContract(groupId, signRequest);
        return ResponseEntity.ok(result);
    }
}
