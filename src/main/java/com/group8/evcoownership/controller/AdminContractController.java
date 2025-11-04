package com.group8.evcoownership.controller;

import com.group8.evcoownership.dto.ContractApprovalRequestDTO;
import com.group8.evcoownership.dto.ContractDTO;
import com.group8.evcoownership.entity.User;
import com.group8.evcoownership.enums.ContractApprovalStatus;
import com.group8.evcoownership.service.ContractService;
import com.group8.evcoownership.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/contracts")
@RequiredArgsConstructor
public class AdminContractController {

    private final ContractService contractService;
    private final UserService userService;

//    @PutMapping("/approve")
//    @PreAuthorize("hasRole('ADMIN')") //mình chi cho admin truy cập thoi
//    public ResponseEntity<ContractDTO> processContractApproval( //method này xử lý duyet hoặc từ chối hợp đồng
//                                                                //nó trả về ResponseEntity chứa contractDTO
//            @Valid @RequestBody ContractApprovalRequestDTO request,
//            @AuthenticationPrincipal String userEmail) { //lấy email của user đã đăng nhập từ thằng security context
//
//        User admin = userService.findByEmail(userEmail);//tìm user admin dựa trên email thoi
//
//        // Validate xem hợp đồng có tồn tại k
//        if (!contractService.contractExists(request.getContractId())) {
//            throw new ResourceNotFoundException("Contract not found with ID: " + request.getContractId());//throw nếu k tìm thấy hợp đồng
//        }
//
//        ContractDTO contract;//khai báo bién contract để mình lưu kết qả xử lý hop dong
//
//        if ("APPROVE".equalsIgnoreCase(request.getAction())) {//check action là APPROVE, approve khong phân biết upper hay lower case
//            contract = contractService.approveContract(request.getContractId(), admin);//sau do gọi service để duyệt hop dong
//
//        } else if ("REJECT".equalsIgnoreCase(request.getAction())) {//check như trên th
//            // Validate ly do mình từ chối hop dong
//            if (request.getReason() == null || request.getReason().isBlank()) {//check ly do từ chối coi null hay rỗng
//                throw new InvalidContractActionException("Rejection reason is required when rejecting a contract");//trow exception k có lý do
//            }
//
//            if (request.getReason().trim().length() < 10) {//kiem tra ly do sau khi trim thoi
//                throw new InvalidContractActionException("Rejection reason must be at least 10 characters");
//            }
//
//            contract = contractService.rejectContract(//goi service để từ chối hop dong
//                    request.getContractId(),//id hop dong mà admin từ chối
//                    request.getReason().trim(),//ly do từ chối sau khi trim
//                    admin//infor cua admin từ chối
//            );
//
//        } else {
//            throw new InvalidContractActionException(
//                    "Invalid action. Only 'APPROVE' or 'REJECT' are allowed"
//            );
//        }
//        return ResponseEntity.ok(contract);
//    }

    @PutMapping("/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ContractDTO> processContractApproval(
            @Valid @RequestBody ContractApprovalRequestDTO request,
            @AuthenticationPrincipal String userEmail) {

        User admin = userService.findByEmail(userEmail);

        ContractDTO contract = contractService.processContractApproval(
                request.getContractId(),
                request.getAction(),
                request.getReason(),
                admin
        );

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
