package com.group8.evcoownership.controller;

import com.group8.evcoownership.dto.ContractGenerationRequest;
import com.group8.evcoownership.dto.ContractGenerationResponse;
import com.group8.evcoownership.service.ContractGenerationService;
import com.group8.evcoownership.service.ContractService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/contracts")
@RequiredArgsConstructor
public class ContractController {

    private final ContractService contractService;
    private final ContractGenerationService contractGenerationService;

    /**
     * Tạo hợp đồng cho group (chỉ admin group)
     */
    @PostMapping("/generate/{groupId}")
    @PreAuthorize("@ownershipGroupService.isGroupAdmin(authentication.name, #groupId)")
    public ResponseEntity<ContractGenerationResponse> generateContract(
            @PathVariable Long groupId,
            @Valid @RequestBody ContractGenerationRequest request) {

        ContractGenerationResponse response = contractGenerationService.generateContract(groupId, request);
        return ResponseEntity.ok(response);
    }

    /**
     * Xem trước hợp đồng HTML
     */
    @GetMapping("/preview/{groupId}")
    @PreAuthorize("@ownershipGroupService.isGroupAdmin(authentication.name, #groupId)")
    public ResponseEntity<String> previewContract(@PathVariable Long groupId) {
        String htmlContent = contractGenerationService.generateHtmlPreview(groupId);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.TEXT_HTML);

        return ResponseEntity.ok()
                .headers(headers)
                .body(htmlContent);
    }

    /**
     * Export hợp đồng thành PDF
     */
    @GetMapping("/export/{groupId}/pdf")
    @PreAuthorize("@ownershipGroupService.isGroupAdmin(authentication.name, #groupId)")
    public ResponseEntity<byte[]> exportContractPdf(@PathVariable Long groupId) {
        byte[] pdfBytes = contractGenerationService.exportToPdf(groupId);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDispositionFormData("attachment", "contract-" + groupId + ".pdf");

        return ResponseEntity.ok()
                .headers(headers)
                .body(pdfBytes);
    }

    /**
     * Lấy thông tin contract của group
     */
    @GetMapping("/{groupId}")
    @PreAuthorize("@ownershipGroupService.isGroupMember(authentication.name, #groupId)")
    public ResponseEntity<Map<String, Object>> getContractInfo(@PathVariable Long groupId) {
        Map<String, Object> contractInfo = contractService.getContractInfo(groupId);
        return ResponseEntity.ok(contractInfo);
    }

    /**
     * Ký hợp đồng (chỉ admin group)
     */
    @PostMapping("/{groupId}/sign")
    @PreAuthorize("@ownershipGroupService.isGroupAdmin(authentication.name, #groupId)")
    public ResponseEntity<Map<String, Object>> signContract(
            @PathVariable Long groupId,
            @RequestBody Map<String, Object> signRequest) {

        Map<String, Object> result = contractService.signContract(groupId, signRequest);
        return ResponseEntity.ok(result);
    }
}
