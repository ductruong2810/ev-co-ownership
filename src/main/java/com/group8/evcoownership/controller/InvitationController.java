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
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
@Tag(name = "Invitations", description = "Quản lý lời mời tham gia nhóm")
public class InvitationController {

    private final InvitationService invitationService;

    /**
     * Tạo lời mời: inviter lấy từ token, groupId ở path, service tự set expire 48h & gửi email
     */
    @PostMapping("/groups/{groupId}/invitations")
    @Operation(summary = "Tạo lời mời", description = "Tạo lời mời tham gia nhóm, tự động gửi email và đặt thời hạn 48h")
    public ResponseEntity<InvitationResponse> create(
            @PathVariable Long groupId,
            @RequestBody @Valid InvitationCreateRequest req,
            Authentication auth
    ) {
        return ResponseEntity.ok(invitationService.create(groupId, req, auth));
    }

    /**
     * Lấy tất cả lời mời của 1 group (chỉ inviter/admin/staff xem được – check trong service)
     */
    @GetMapping("/groups/{groupId}/invitations")
    @Operation(summary = "Danh sách lời mời theo nhóm", description = "Lấy danh sách tất cả lời mời của một nhóm với phân trang")
    public ResponseEntity<Page<InvitationResponse>> listByGroup(
            @PathVariable Long groupId,
            @RequestParam(defaultValue = "0") @PositiveOrZero int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            Authentication auth
    ) {
        return ResponseEntity.ok(invitationService.listByGroup(groupId, page, size, auth));
    }

    /**
     * Lấy chi tiết 1 invitation
     */
    @GetMapping("/invitations/{invitationId}")
    @Operation(summary = "Chi tiết lời mời", description = "Lấy thông tin chi tiết của một lời mời cụ thể")
    public ResponseEntity<InvitationResponse> getOne(
            @PathVariable Long invitationId,
            Authentication auth
    ) {
        return ResponseEntity.ok(invitationService.getOne(invitationId, auth));
    }

    /**
     * Resend email (tăng ResendCount, cập nhật LastSentAt)
     */
    @PostMapping("/invitations/{invitationId}/resend")
    @Operation(summary = "Gửi lại email", description = "Gửi lại email lời mời và cập nhật số lần gửi lại")
    public ResponseEntity<Void> resend(
            @PathVariable Long invitationId,
            Authentication auth
    ) {
        invitationService.resend(invitationId, auth);
        return ResponseEntity.ok().build();
    }

    /**
     * Hủy/Expire ngay lập tức
     */
    @PostMapping("/invitations/{invitationId}/expire")
    @Operation(summary = "Hủy lời mời", description = "Hủy lời mời ngay lập tức")
    public ResponseEntity<Void> expireNow(
            @PathVariable Long invitationId,
            Authentication auth
    ) {
        invitationService.expireNow(invitationId, auth);
        return ResponseEntity.ok().build();
    }

    /**
     * Người được mời accept bằng OTP (cần đã login)
     */
    @PostMapping("/invitations/accept")
    @Operation(summary = "Chấp nhận lời mời", description = "Chấp nhận lời mời tham gia nhóm bằng OTP")
    public ResponseEntity<InvitationResponse> accept(
            @RequestBody @Valid InvitationAcceptRequest req,
            Authentication auth
    ) {
        return ResponseEntity.ok(invitationService.accept(req, auth));
    }
}

