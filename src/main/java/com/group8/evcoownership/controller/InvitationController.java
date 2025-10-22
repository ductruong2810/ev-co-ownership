package com.group8.evcoownership.controller;


import com.group8.evcoownership.dto.InvitationAcceptRequest;
import com.group8.evcoownership.dto.InvitationCreateRequest;
import com.group8.evcoownership.dto.InvitationResponse;
import com.group8.evcoownership.service.InvitationService;
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
public class InvitationController {

    private final InvitationService invitationService;

    /**
     * Tạo lời mời: inviter lấy từ token, groupId ở path, service tự set expire 48h & gửi email
     */
    @PostMapping("/groups/{groupId}/invitations")
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
    public ResponseEntity<InvitationResponse> accept(
            @RequestBody @Valid InvitationAcceptRequest req,
            Authentication auth
    ) {
        return ResponseEntity.ok(invitationService.accept(req, auth));
    }
}

