package com.group8.evcoownership.service;

import com.group8.evcoownership.dto.InvitationAcceptRequestDTO;
import com.group8.evcoownership.dto.InvitationCreateRequestDTO;
import com.group8.evcoownership.dto.InvitationResponseDTO;
import com.group8.evcoownership.dto.OwnershipShareCreateRequestDTO;
import com.group8.evcoownership.entity.Invitation;
import com.group8.evcoownership.entity.OwnershipGroup;
import com.group8.evcoownership.entity.User;
import com.group8.evcoownership.enums.GroupRole;
import com.group8.evcoownership.enums.GroupStatus;
import com.group8.evcoownership.enums.InvitationStatus;
import com.group8.evcoownership.repository.InvitationRepository;
import com.group8.evcoownership.repository.OwnershipGroupRepository;
import com.group8.evcoownership.repository.OwnershipShareRepository;
import com.group8.evcoownership.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class InvitationService {

    private static final int DEFAULT_INVITE_TTL_HOURS = 48;

    private final InvitationRepository invitationRepo;
    private final OwnershipGroupRepository groupRepo;
    private final OwnershipShareRepository shareRepo;
    private final UserRepository userRepo;
    private final EmailService emailService;
    private final UserDocumentValidationService userDocumentValidationService;
    private final OwnershipShareService shareService;

    // =========================================================
    // =============== CREATE (Tạo hoặc Resend) ================
    // =========================================================

    /**
     * Gửi lời mời cho 1 email trong group:
     * - Nếu chưa có lời mời PENDING → tạo mới và gửi mail
     * - Nếu đã có lời mời PENDING → cập nhật resendCount, OTP, expiresAt, rồi gửi lại mail
     */
    @Transactional
    public InvitationResponseDTO create(Long groupId,
                                        InvitationCreateRequestDTO req,
                                        Authentication auth) {

        // Lấy group + kiểm tra tồn tại và trạng thái ACTIVE
        OwnershipGroup group = groupRepo.findById(groupId)
                .orElseThrow(() -> new EntityNotFoundException("Group not found"));
        ensureGroupActive(group);

        // Lấy inviter từ token (email trong Authentication)
        User inviter = userRepo.findByEmail(auth.getName())
                .orElseThrow(() -> new EntityNotFoundException("Inviter not found"));

        // Kiểm tra inviter có thuộc group không
        boolean isMember = shareRepo.existsByGroup_GroupIdAndUser_UserId(groupId, inviter.getUserId());
        if (!isMember) throw new AccessDeniedException("Not a member of this group");

        // Kiểm tra có invitation PENDING nào đã tồn tại cho email này trong group
        Invitation existing = invitationRepo
                .findByGroup_GroupIdAndInviteeEmailIgnoreCaseAndStatus(
                        groupId, req.inviteeEmail(), InvitationStatus.PENDING)
                .orElse(null);

        // Tạo token/OTP mới và thời hạn mới
        String otp = genOtp();
        String token = genToken();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiresAt = now.plusHours(DEFAULT_INVITE_TTL_HOURS);

        // Nếu có invitation PENDING → resend (update lại)
        if (existing != null) {
            existing.setOtpCode(otp);
            existing.setExpiresAt(expiresAt);
            existing.setLastSentAt(now);
            existing.setResendCount(existing.getResendCount() == null ? 1 : existing.getResendCount() + 1);

            invitationRepo.save(existing);

            emailService.sendInvitationEmail(
                    existing.getInviteeEmail(),
                    group.getGroupName(),
                    inviter.getFullName(),
                    // giữ token cũ để user cũ vẫn valid link
                    otp,
                    expiresAt,
                    existing.getSuggestedPercentage()
            );

            // Ghi log cho dễ debug
            System.out.printf("Resent invitation to %s (group %s)%n",
                    existing.getInviteeEmail(), group.getGroupName());

            return toDto(existing);
        }

        // Nếu chưa có invitation PENDING → tạo mới
        Invitation inv = Invitation.builder()
                .group(group)
                .inviter(inviter)
                .inviteeEmail(req.inviteeEmail())
                .token(token)
                .otpCode(otp)
                .status(InvitationStatus.PENDING)
                .suggestedPercentage(req.suggestedPercentage())
                .expiresAt(expiresAt)
                .resendCount(0)
                .lastSentAt(now)
                .createdAt(now)
                .build();

        Invitation saved = invitationRepo.save(inv);

        emailService.sendInvitationEmail(
                req.inviteeEmail(),
                group.getGroupName(),
                inviter.getFullName(),
                otp,
                expiresAt,
                req.suggestedPercentage()
        );

        System.out.printf("Sent new invitation to %s (group %s)%n",
                req.inviteeEmail(), group.getGroupName());

        return toDto(saved);
    }

    // =========================================================
    // =================== RESEND / EXPIRE =====================
    // =========================================================

    /**
     * Dùng cho backend hoặc admin gửi lại mail thủ công (nếu cần)
     */
    @Transactional
    public void resend(Long invitationId, Authentication auth) {
        Invitation inv = invitationRepo.findById(invitationId)
                .orElseThrow(() -> new EntityNotFoundException("Invitation not found"));

        if (inv.getStatus() != InvitationStatus.PENDING)
            throw new IllegalStateException("Only PENDING invitation can be resent");
        if (isExpired(inv))
            throw new IllegalStateException("Invitation expired");

        // Kiểm tra user nay dang nhap co duoc quyen gui resend khong
        validateResendPermission(inv, auth);

        // Cập nhật OTP mới và resendCount
        String newOtp = genOtp();
        inv.setOtpCode(newOtp);
        inv.setLastSentAt(LocalDateTime.now());
        inv.setResendCount(inv.getResendCount() == null ? 1 : inv.getResendCount() + 1);

        invitationRepo.save(inv);

        emailService.sendInvitationEmail(
                inv.getInviteeEmail(),
                inv.getGroup().getGroupName(),
                inv.getInviter() != null ? inv.getInviter().getFullName() : "A group member",
                newOtp,
                inv.getExpiresAt(),
                inv.getSuggestedPercentage()
        );

        System.out.printf("Manual resend invitation #%d to %s%n", inv.getInvitationId(), inv.getInviteeEmail());
    }

    /**
     * Hết hạn ngay lập tức (inviter, admin group, hoặc staff/admin)
     */
    @Transactional
    public void expireNow(Long invitationId, Authentication auth) {
        Invitation inv = invitationRepo.findById(invitationId)
                .orElseThrow(() -> new EntityNotFoundException("Invitation not found"));
        validateExpirePermission(inv, auth);

        if (inv.getStatus() != InvitationStatus.PENDING)
            throw new IllegalStateException("Only PENDING invitation can be expired");

        inv.setStatus(InvitationStatus.EXPIRED);
        invitationRepo.save(inv);

        System.out.printf("Expired invitation #%d manually%n", inv.getInvitationId());
    }

    // --- lay danh sach invitation theo groupId
    @Transactional
    public Page<InvitationResponseDTO> listByGroup(Long groupId, int page, int size, Authentication auth) {
        // Kiểm tra quyền xem danh sách
        validateListPermission(groupId, auth);

        // Tạo đối tượng Pageable
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));

        // Gọi repo để lấy danh sách Invitation, sau đó map sang DTO
        return invitationRepo.findByGroup_GroupId(groupId, pageable)
                .map(this::toDto);
    }

    /**
     * Lấy chi tiết 1 lời mời (Invitation) theo ID.
     * - Chỉ cho phép người có quyền xem: người mời (inviter), admin group, hoặc staff/admin.
     */
    public InvitationResponseDTO getOne(Long invitationId, Authentication auth) {
        // Tìm lời mời trong DB
        Invitation inv = invitationRepo.findById(invitationId)
                .orElseThrow(() -> new EntityNotFoundException("Invitation not found"));

        // Kiểm tra quyền xem (dùng helper validateViewPermission bên dưới)
        validateViewPermission(inv, auth);

        // Trả về DTO (ẩn bớt thông tin nhạy cảm nếu cần)
        return toDto(inv);
    }


    // =========================================================
    // ======================= ACCEPT ==========================
    // =========================================================

    /**
     * User nhập OTP để chấp nhận lời mời (đã login).
     */
    @Transactional
    public InvitationResponseDTO accept(InvitationAcceptRequestDTO req, Authentication auth) {
        Invitation inv = invitationRepo.findByOtpCodeAndStatus(
                req.otp(), InvitationStatus.PENDING
        ).orElseThrow(() -> new EntityNotFoundException("Invitation not found"));

        if (isExpired(inv))
            throw new IllegalStateException("Invitation expired");

        OwnershipGroup group = inv.getGroup();
        ensureGroupActive(group);

        // Lấy user hiện tại
        User user = userRepo.findByEmail(auth.getName())
                .orElseThrow(() -> new EntityNotFoundException("User not found"));

        // Xác minh email trùng với người được mời
        if (!inv.getInviteeEmail().trim().equalsIgnoreCase(user.getEmail().trim()))
            throw new AccessDeniedException("This invitation is not for your email address");

        // Không cho trùng membership
        if (shareRepo.existsByGroup_GroupIdAndUser_UserId(group.getGroupId(), user.getUserId()))
            throw new IllegalStateException("You are already a member of this group");

        // Kiểm tra capacity
        long members = shareRepo.countByGroup_GroupId(group.getGroupId());
        if (group.getMemberCapacity() != null && members + 1 > group.getMemberCapacity())
            throw new IllegalStateException("Member capacity exceeded");

        // Kiểm tra giấy tờ user
        userDocumentValidationService.validateUserDocuments(user.getUserId());

        // Thêm user vào group với % sở hữu tạm = 0%
        var addReq = new OwnershipShareCreateRequestDTO(
                user.getUserId(), group.getGroupId(), BigDecimal.ZERO
        );
        shareService.addGroupShare(addReq);

        // Cập nhật invitation -> ACCEPTED
        inv.setStatus(InvitationStatus.ACCEPTED);
        inv.setAcceptedAt(LocalDateTime.now());
        inv.setAcceptedBy(user);
        Invitation saved = invitationRepo.save(inv);

        System.out.printf(" User %s accepted invitation for group %s%n",
                user.getEmail(), group.getGroupName());

        return toDto(saved);
    }

    // =========================================================
    // ===================== HELPERS ===========================
    // =========================================================

    private void ensureGroupActive(OwnershipGroup g) {
        if (g.getStatus() != GroupStatus.ACTIVE)
            throw new IllegalStateException("Group is not ACTIVE");
    }

    private boolean isExpired(Invitation inv) {
        return inv.getExpiresAt() != null && LocalDateTime.now().isAfter(inv.getExpiresAt());
    }

    private String genToken() {
        return UUID.randomUUID().toString().replace("-", "")
                + UUID.randomUUID().toString().replace("-", "");
    }

    private String genOtp() {
        var rnd = new SecureRandom();
        int x = rnd.nextInt(1_000_000);
        return String.format("%06d", x);
    }

    private boolean isStaffOrAdmin(User u) {
        return u.getRole() != null
                && u.getRole().getRoleName() != null
                && (u.getRole().getRoleName().name().equalsIgnoreCase("STAFF")
                || u.getRole().getRoleName().name().equalsIgnoreCase("ADMIN"));
    }

    private InvitationResponseDTO toDto(Invitation i) {
        return new InvitationResponseDTO(
                i.getInvitationId(),
                i.getGroup().getGroupId(),
                i.getInviteeEmail(),
                i.getStatus(),
                i.getSuggestedPercentage(),
                i.getExpiresAt(),
                i.getResendCount(),
                i.getLastSentAt(),
                i.getAcceptedAt(),
                i.getInviter() != null ? i.getInviter().getUserId() : null
        );
    }

    // =========================================================
    // ================ AUTHORIZATION HELPERS ==================
    // =========================================================

    private void validateResendPermission(Invitation inv, Authentication auth) {
        validateInvitationPermission(inv, auth);
    }

    private void validateExpirePermission(Invitation inv, Authentication auth) {
        validateInvitationPermission(inv, auth);
    }

    private void validateViewPermission(Invitation inv, Authentication auth) {
        validateInvitationPermission(inv, auth);
    }

    private void validateListPermission(Long groupId, Authentication auth) {
        User actor = getCurrentUser(auth);

        boolean isGroupAdmin = shareRepo.existsByGroup_GroupIdAndUser_UserIdAndGroupRole(
                groupId, actor.getUserId(), GroupRole.ADMIN);
        boolean isStaffAdmin = isStaffOrAdmin(actor);

        if (!(isGroupAdmin || isStaffAdmin))
            throw new AccessDeniedException("Forbidden");
    }

    private void validateInvitationPermission(Invitation inv, Authentication auth) {
        User actor = getCurrentUser(auth);
        boolean hasPermission = isInviterForInvitation(inv, actor)
                || isGroupAdminForInvitation(inv, actor)
                || isStaffOrAdmin(actor);

        if (!hasPermission)
            throw new AccessDeniedException("Forbidden");
    }

    private User getCurrentUser(Authentication auth) {
        return userRepo.findByEmail(auth.getName())
                .orElseThrow(() -> new EntityNotFoundException("User not found"));
    }

    private boolean isInviterForInvitation(Invitation inv, User actor) {
        return inv.getInviter() != null
                && inv.getInviter().getUserId().equals(actor.getUserId());
    }

    private boolean isGroupAdminForInvitation(Invitation inv, User actor) {
        return shareRepo.existsByGroup_GroupIdAndUser_UserIdAndGroupRole(
                inv.getGroup().getGroupId(),
                actor.getUserId(),
                GroupRole.ADMIN
        );
    }
}
