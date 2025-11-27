package com.group8.evcoownership.controller;

import com.group8.evcoownership.dto.GroupBookingDTO;
import com.group8.evcoownership.dto.ReviewDocumentRequestDTO;
import com.group8.evcoownership.dto.UserGroupBookingsResponseDTO;
import com.group8.evcoownership.dto.UserProfileResponseDTO;
import com.group8.evcoownership.enums.FundType;
import com.group8.evcoownership.service.FundService;
import com.group8.evcoownership.service.StaffService;
import com.group8.evcoownership.utils.AuthUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/staff")
@Slf4j
@Tag(name = "Staff", description = "Chức năng dành cho nhân viên và quản trị viên")
@PreAuthorize("isAuthenticated()")
public class StaffController {

    @Autowired
    private StaffService staffService;
    // Service chứa toàn bộ buisiness logic cho staff va admin

    @Autowired
    private FundService fundService;

    //========= Lấy danh sách user =========
    @GetMapping("/users")
    @PreAuthorize("hasRole('STAFF') or hasRole('ADMIN')")
    @Operation(summary = "Danh sách người dùng", description = "Lấy danh sách tất cả người dùng với khả năng lọc theo trạng thái và tài liệu")
    public ResponseEntity<List<UserProfileResponseDTO>> getAllUsers(
            @RequestParam(required = false) String status, // filter theo trạng thái ACTIVE, BANNED
            @RequestParam(required = false) String documentStatus) { // filter theo trạng thái tài liệu (PENDING, APPROVED)

        // log hiện trên server cho dễ nhìn
        log.info("Staff fetching all users - status: {}, documentStatus: {}", status, documentStatus);
        // mình gọi service để lấy danh sách user
        List<UserProfileResponseDTO> users = staffService.getAllUsers(status, documentStatus);
        // trả về 200ok + list user profile
        return ResponseEntity.ok(users);
    }

    // ========= Lấy chi tiết 1 user=========
    @GetMapping("/users/{userId}")
    @PreAuthorize("hasRole('STAFF') or hasRole('ADMIN')")
    @Operation(summary = "Chi tiết người dùng", description = "Lấy thông tin chi tiết của một người dùng cụ thể")
    public ResponseEntity<UserProfileResponseDTO> getUserDetail(@PathVariable Long userId) {
        log.info("Staff fetching user detail for userId: {}", userId);

        // Lấy profile chi tiết 1 user theo id (thông tin cá nhân, giay tờ)
        UserProfileResponseDTO user = staffService.getUserDetail(userId);
        //tra 200ok + user profile
        return ResponseEntity.ok(user);
    }

    // ========= Lấy danh sách group hoặc booking theo user=========
    //11/3/2025
    @GetMapping("/users/{userId}/groups")
    public ResponseEntity<List<GroupBookingDTO>> getGroupsByUserId(@PathVariable Long userId) {
        // Lấy danh sách group + booking mà user này tham gia
        List<GroupBookingDTO> groups = staffService.getGroupsByUserId(userId);
        return ResponseEntity.ok(groups);
    }

    // ========= Lấy danh sách user có document pending =========
    @GetMapping("/documents/pending")
    @PreAuthorize("hasRole('STAFF') or hasRole('ADMIN')")
    @Operation(summary = "Tài liệu chờ duyệt", description = "Lấy danh sách người dùng có tài liệu chờ duyệt")
    public ResponseEntity<List<UserProfileResponseDTO>> getUsersWithPendingDocuments() {
        log.info("Staff fetching users with pending documents");
        // Gọi service lấy các user có document đang ở trạng thái PENDING
        List<UserProfileResponseDTO> users = staffService.getUsersWithPendingDocuments();
        return ResponseEntity.ok(users);
    }

    // ========= Review document APPROVE/REJECT và reason =========
    @PostMapping("/documents/review/{documentId}")
    @PreAuthorize("hasRole('STAFF') or hasRole('ADMIN')")
    @Operation(summary = "Duyệt tài liệu", description = "Nhân viên duyệt tài liệu của người dùng")
    public ResponseEntity<Map<String, String>> reviewDocument(
            @PathVariable Long documentId, //id của tài liệu cần duyêt
            @Valid @RequestBody ReviewDocumentRequestDTO request, // request gồm trạng thái mới và reason (nếu reject)
            Authentication authentication)  { // chứa thông tin staff hiện tại (JWT)

        // Lấy email của staff từ Authentication được set bởi thằng JwtAuthenticationFilter
        String staffEmail = AuthUtils.getCurrentUserEmail(authentication);
        log.info("Staff {} reviewing document {}", staffEmail, documentId);

        // Gọi service xử lý review (approve/reject) tài liệu
        String message = staffService.reviewDocument(documentId, request, staffEmail);
        return ResponseEntity.ok(Map.of("message", message));
    }

    // ========= Phê duyệt document =========
    @PostMapping("/documents/approve/{documentId}")
    @PreAuthorize("hasRole('STAFF') or hasRole('ADMIN')")
    @Operation(summary = "Phê duyệt tài liệu", description = "Phê duyệt tài liệu của người dùng")
    public ResponseEntity<Map<String, String>> approveDocument(
            @PathVariable Long documentId,
            Authentication authentication) {

        String staffEmail = AuthUtils.getCurrentUserEmail(authentication);
        log.info("Staff {} approving document {}", staffEmail, documentId);

        // Service thực hiện logic approve đơn giản, chỉ cần documentId + staffEmail
        String message = staffService.approveDocument(documentId, staffEmail);
        return ResponseEntity.ok(Map.of("message", message));
    }

    // ========= Từ chối document với lý do =========
    @PostMapping("/documents/reject/{documentId}")
    @PreAuthorize("hasRole('STAFF') or hasRole('ADMIN')")
    @Operation(summary = "Từ chối tài liệu", description = "Từ chối tài liệu của người dùng với lý do cụ thể")
    public ResponseEntity<Map<String, String>> rejectDocument(
            @PathVariable Long documentId,
            @RequestParam String reason, //ở đây truyền thêm lý do mình từ chối
            Authentication authentication) {

        String staffEmail = AuthUtils.getCurrentUserEmail(authentication);
        log.info("Staff {} rejecting document {}", staffEmail, documentId);

        // Service xử lý reject + lưu reason + log lại staff xử lý
        String message = staffService.rejectDocument(documentId, reason, staffEmail);
        return ResponseEntity.ok(Map.of("message", message));
    }

    @DeleteMapping("/users/{userId}")
    @PreAuthorize("hasRole('STAFF') or hasRole('ADMIN')")
    @Operation(summary = "Xóa người dùng (soft delete)", description = "Đặt trạng thái người dùng thành BANNED để vô hiệu hóa tài khoản an toàn")
    public ResponseEntity<Map<String, String>> deleteUser(@PathVariable Long userId) {
        String message = staffService.deleteUser(userId);
        return ResponseEntity.ok(Map.of("message", message));
    }

    @GetMapping("/users/qrcodes")
    @PreAuthorize("hasRole('STAFF') or hasRole('ADMIN')")
    @Operation(summary = "Lấy QR Code tất cả users", description = "Lấy tất cả QR Code của tất cả users từ các group và booking (mỗi trang tối đa 10 users)")
    public ResponseEntity<Page<UserGroupBookingsResponseDTO>> getAllUsersQRCode(
            @RequestParam(defaultValue = "0") int page, //số trang mac dinh 0
            @RequestParam(defaultValue = "10") int size) { //tối đa là 10

        // Đảm bảo size không vượt quá 10 vì nếu k trả nhiều quá scroll mệt và rối
        if (size > 10) {
            size = 10;
        }

        log.info("Staff fetching all users QR codes - page: {}, size: {}", page, size);
        // Gọi service lấy danh sách user + group + booking + QRCode, trả dưới dạng Page
        Page<UserGroupBookingsResponseDTO> response = staffService.getAllUsersQRCode(page, size);
        return ResponseEntity.ok(response);
    }

    // ========= Xuất báo cáo tài chính tổng hợp cho tất cả groups =========
    @GetMapping("/financial-reports/export-all")
    @PreAuthorize("hasRole('STAFF') or hasRole('ADMIN')")
    @Operation(summary = "Xuất báo cáo tài chính tổng hợp", description = "Xuất báo cáo tài chính CSV cho tất cả các nhóm trong hệ thống")
    public ResponseEntity<String> exportAllGroupsFinancialReport(
            @RequestParam(required = false) FundType fundType,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {

        log.info("Staff exporting financial reports for all groups - fundType: {}, from: {}, to: {}", fundType, from, to);

        String csvContent = fundService.generateAllGroupsFinancialReportCSV(fundType, from, to);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.TEXT_PLAIN);
        String filename = "financial_reports_all_groups_" + LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".csv";
        headers.setContentDispositionFormData("attachment", filename);

        return ResponseEntity.ok()
                .headers(headers)
                .body(csvContent);
    }

}
