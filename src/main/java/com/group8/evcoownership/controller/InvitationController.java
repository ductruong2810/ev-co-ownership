package com.group8.evcoownership.controller;

import com.group8.evcoownership.dto.InvitationAcceptRequest;
import com.group8.evcoownership.dto.InvitationCreateRequest;
import com.group8.evcoownership.dto.InvitationResponse;
import com.group8.evcoownership.service.InvitationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
@Tag(name = "Invitations", description = "Quản lý lời mời tham gia nhóm (gửi, resend tự động, accept, expire)")
public class InvitationController {

    private final InvitationService invitationService;

    // ======================================================
    // =============== CREATE / RESEND (AUTO) ===============
    // ======================================================

    /**
     * 📨 Gửi lời mời (hoặc tự động resend nếu đang PENDING)
     * - Inviter lấy từ token
     * - groupId lấy từ path
     * - Service tự kiểm tra nếu có invitation PENDING thì resend thay vì tạo mới
     */
    @PostMapping("/groups/{groupId}/invitations")
    @PreAuthorize("@ownershipGroupService.isGroupAdmin(authentication.name, #groupId)")
    @Operation(
            summary = "Gửi lời mời (hoặc tự động gửi lại)",
            description = """
                    Tạo mới lời mời nếu chưa tồn tại. 
                    Nếu email đã có invitation PENDING trong cùng group, hệ thống sẽ tự động resend (cập nhật OTP, thời hạn, gửi lại mail).
                    """
    )
    public ResponseEntity<InvitationResponse> createOrResend(
            @PathVariable Long groupId,
            @RequestBody @Valid InvitationCreateRequest req,
            Authentication auth
    ) {
        InvitationResponse response = invitationService.create(groupId, req, auth);
        return ResponseEntity.ok(response);
    }

    // ======================================================
    // =================== LIST / DETAILS ===================
    // ======================================================

    /**
     * Lấy danh sách lời mời của group (chỉ group member / staff / admin)
     */
    @GetMapping("/groups/{groupId}/invitations")
    @Operation(summary = "Danh sách lời mời theo nhóm", description = "Lấy danh sách lời mời của một group (phân trang).")
    public ResponseEntity<Page<InvitationResponse>> listByGroup(
            @PathVariable Long groupId,
            @RequestParam(defaultValue = "0") @PositiveOrZero int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            Authentication auth
    ) {
        return ResponseEntity.ok(invitationService.listByGroup(groupId, page, size, auth));
    }

    /**
     * Lấy chi tiết 1 invitation cụ thể
     */
    @GetMapping("/invitations/{invitationId}")
    @Operation(summary = "Chi tiết lời mời", description = "Lấy thông tin chi tiết của lời mời cụ thể.")
    public ResponseEntity<InvitationResponse> getOne(
            @PathVariable Long invitationId,
            Authentication auth
    ) {
        return ResponseEntity.ok(invitationService.getOne(invitationId, auth));
    }

    // ======================================================
    // ================== EXPIRE / ACCEPT ===================
    // ======================================================

    /**
     * 🧊 Hủy (expire) lời mời ngay lập tức
     */
    @PostMapping("/invitations/{invitationId}/expire")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Hủy lời mời", description = "Đặt trạng thái lời mời thành EXPIRED ngay lập tức.")
    public ResponseEntity<Void> expireNow(
            @PathVariable Long invitationId,
            Authentication auth
    ) {
        invitationService.expireNow(invitationId, auth);
        return ResponseEntity.ok().build();
    }

    /**
     * ✅ Người được mời chấp nhận bằng OTP
     */
    @PostMapping("/invitations/accept")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Chấp nhận lời mời", description = "Người được mời nhập OTP để tham gia group.")
    public ResponseEntity<InvitationResponse> accept(
            @RequestBody @Valid InvitationAcceptRequest req,
            Authentication auth
    ) {
        return ResponseEntity.ok(invitationService.accept(req, auth));
    }

    // ======================================================
    // ================= OPTIONAL (ADMIN) ===================
    // ======================================================

    /**
     * ⚙️ API gửi lại email thủ công (dành cho admin/backend, không cần dùng trên FE vì create() đã tự xử lý)
     */
    @PostMapping("/invitations/{invitationId}/resend")
    @PreAuthorize("hasAnyRole('ADMIN','STAFF')")
    @Operation(summary = "[Admin] Gửi lại email thủ công", description = "Dùng cho admin hoặc staff gửi lại email một invitation cụ thể.")
    public ResponseEntity<Void> resendManual(
            @PathVariable Long invitationId,
            Authentication auth
    ) {
        invitationService.resend(invitationId, auth);
        return ResponseEntity.ok().build();
    }
}
