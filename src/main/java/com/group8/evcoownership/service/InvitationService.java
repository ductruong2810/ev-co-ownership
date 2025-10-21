package com.group8.evcoownership.service;


import com.group8.evcoownership.dto.InvitationAcceptRequest;
import com.group8.evcoownership.dto.InvitationCreateRequest;
import com.group8.evcoownership.dto.InvitationResponse;
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
import com.group8.evcoownership.repository.UserDocumentRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InvitationService {

    private static final int DEFAULT_INVITE_TTL_HOURS = 48;

    private final InvitationRepository invitationRepo;
    private final OwnershipGroupRepository groupRepo;
    private final OwnershipShareRepository shareRepo;
    private final UserRepository userRepo;
    private final EmailService emailService;
    private final UserDocumentRepository userDocumentRepository;
    private final OwnershipShareService shareService;

    // ===================== CREATE =====================

    /**
     * Tạo invitation cho groupId (từ path). inviter lấy từ token.
     */
    @Transactional
    public InvitationResponse create(Long groupId,
                                     InvitationCreateRequest req,
                                     Authentication auth) {
        OwnershipGroup group = groupRepo.findById(groupId)
                .orElseThrow(() -> new EntityNotFoundException("Group not found"));

        ensureGroupActive(group);

        User inviter = userRepo.findByEmail(auth.getName())
                .orElseThrow(() -> new EntityNotFoundException("Inviter not found"));

        // kiểm tra inviter là member của group (tuỳ bạn có bắt buộc ADMIN/OWNER không)
        boolean isMember = shareRepo.existsByGroup_GroupIdAndUser_UserId(groupId, inviter.getUserId());
        if (!isMember) throw new AccessDeniedException("Not a member of this group");

        // chặn trùng pending invitation theo email trong cùng group
        if (invitationRepo.existsByGroup_GroupIdAndInviteeEmailIgnoreCaseAndStatus(
                groupId, req.inviteeEmail(), InvitationStatus.PENDING)) {
            throw new IllegalStateException("A pending invitation already exists for this email in the group");
        }

        String token = genToken();
        String otp = genOtp();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiresAt = now.plusHours(DEFAULT_INVITE_TTL_HOURS);

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

        // Gửi email mời
        String acceptUrl = buildAcceptUrl(token); // ví dụ đọc từ config FE base URL
        emailService.sendInvitationEmail(
                req.inviteeEmail(),
                group.getGroupName(),
                inviter.getFullName(),
                token,
                otp,
                expiresAt,
                req.suggestedPercentage(),
                acceptUrl
        );

        return toDto(saved);
    }

    // ===================== LIST/VIEW =====================

    /**
     * Chỉ member group (hoặc staff/admin) được xem danh sách lời mời của group.
     */
    public Page<InvitationResponse> listByGroupSecured(Long groupId, Pageable pageable, Authentication auth) {
        User viewer = userRepo.findByEmail(auth.getName())
                .orElseThrow(() -> new EntityNotFoundException("User not found"));

        boolean canView =
                shareRepo.existsByGroup_GroupIdAndUser_UserId(groupId, viewer.getUserId())
                        || isStaffOrAdmin(viewer);

        if (!canView) throw new AccessDeniedException("Forbidden");

        if (!groupRepo.existsById(groupId)) throw new EntityNotFoundException("Group not found");

        return invitationRepo.findByGroup_GroupId(groupId, pageable).map(this::toDto);
    }

    /**
     * Kiểm tra token còn hợp lệ để FE preload form accept.
     */
    public InvitationResponse validateToken(String token) {
        Invitation inv = invitationRepo.findByToken(token)
                .orElseThrow(() -> new EntityNotFoundException("Invitation not found"));
        if (inv.getStatus() != InvitationStatus.PENDING) {
            throw new IllegalStateException("Invitation is not PENDING");
        }
        if (isExpired(inv)) throw new IllegalStateException("Invitation expired");
        return toDto(inv);
    }

    // ===================== RESEND / EXPIRE =====================

    /**
     * Resend OTP/email cho invitation (giới hạn quyền: inviter hoặc admin group).
     */
    @Transactional
    public void resend(Long invitationId, Authentication auth) {
        Invitation inv = invitationRepo.findById(invitationId)
                .orElseThrow(() -> new EntityNotFoundException("Invitation not found"));

        if (inv.getStatus() != InvitationStatus.PENDING) {
            throw new IllegalStateException("Only PENDING invitation can be resent");
        }
        if (isExpired(inv)) throw new IllegalStateException("Invitation expired");

        // Kiểm tra quyền: inviter, admin group, hoặc staff/admin
        validateResendPermission(inv, auth);

        // phát sinh OTP mới, cập nhật thời điểm gửi + tăng resendCount (token giữ nguyên)
        String newOtp = genOtp();
        inv.setOtpCode(newOtp);
        inv.setLastSentAt(LocalDateTime.now());
        inv.setResendCount(inv.getResendCount() == null ? 1 : inv.getResendCount() + 1);

        invitationRepo.save(inv);

        String acceptUrl = buildAcceptUrl(inv.getToken());
        emailService.sendInvitationEmail(
                inv.getInviteeEmail(),
                inv.getGroup().getGroupName(),
                inv.getInviter() != null ? inv.getInviter().getFullName() : "A group member",
                inv.getToken(),
                newOtp,
                inv.getExpiresAt(),
                inv.getSuggestedPercentage(),
                acceptUrl
        );

        // No return needed; controller responds 200 OK
    }

    /**
     * Hết hạn ngay (inviter, admin group, hoặc staff/admin).
     */
    @Transactional
    public void expireNow(Long invitationId, Authentication auth) {
        Invitation inv = invitationRepo.findById(invitationId)
                .orElseThrow(() -> new EntityNotFoundException("Invitation not found"));

        validateExpirePermission(inv, auth);

        if (inv.getStatus() != InvitationStatus.PENDING) {
            throw new IllegalStateException("Only PENDING invitation can be expired");
        }

        inv.setStatus(InvitationStatus.EXPIRED);
//        inv.setUpdatedAt(LocalDateTime.now());
        invitationRepo.save(inv);
    }

    // ===================== ACCEPT =====================

    /**
     * Accept lời mời bằng token + OTP + acceptUserId (+ percentage).
     */
    @Transactional
    public InvitationResponse accept(InvitationAcceptRequest req) {
        Invitation inv = invitationRepo.findByToken(req.token())
                .orElseThrow(() -> new EntityNotFoundException("Invitation not found"));

        if (inv.getStatus() != InvitationStatus.PENDING) {
            throw new IllegalStateException("Invitation is not PENDING");
        }
        if (isExpired(inv)) throw new IllegalStateException("Invitation expired");
        if (!inv.getOtpCode().equals(req.otp())) {
            throw new IllegalArgumentException("Invalid OTP");
        }

        OwnershipGroup group = inv.getGroup();
        ensureGroupActive(group);

        // user accept phải tồn tại và khớp email mời (chống giả mạo)
        User user = userRepo.findById(req.acceptUserId())
                .orElseThrow(() -> new EntityNotFoundException("User not found"));

        String invited = inv.getInviteeEmail() == null ? "" : inv.getInviteeEmail().trim().toLowerCase(Locale.ROOT);
        String actual = user.getEmail() == null ? "" : user.getEmail().trim().toLowerCase(Locale.ROOT);
        if (!invited.equals(actual)) {
            throw new AccessDeniedException("This invitation is not for the provided user");
        }

        // không cho trùng membership
        if (shareRepo.existsByGroup_GroupIdAndUser_UserId(group.getGroupId(), user.getUserId())) {
            throw new IllegalStateException("User is already a member of this group");
        }

        // capacity
        long members = shareRepo.countByGroup_GroupId(group.getGroupId());
        if (group.getMemberCapacity() != null && members + 1 > group.getMemberCapacity()) {
            throw new IllegalStateException("Member capacity exceeded");
        }

        // % sở hữu
        var percent = req.ownershipPercentage();
        if (percent == null || percent.compareTo(java.math.BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Ownership percentage must be > 0");
        }

        // Kiểm tra user có đầy đủ giấy tờ đã được duyệt không
        validateUserDocuments(user.getUserId());

        // Dùng OwnershipShareService để add share + auto-activate
        var addReq = new com.group8.evcoownership.dto.OwnershipShareCreateRequest(
                user.getUserId(),
                group.getGroupId(),
                percent
        );
        shareService.addGroupShare(addReq);

        // cập nhật invitation → ACCEPTED
        inv.setStatus(InvitationStatus.ACCEPTED);
        inv.setAcceptedAt(LocalDateTime.now());
        inv.setAcceptedBy(user);
//        inv.setUpdatedAt(LocalDateTime.now());
        Invitation saved = invitationRepo.save(inv);

        return toDto(saved);
    }

    // ===================== HELPERS =====================
    private void ensureGroupActive(OwnershipGroup g) {
        if (g.getStatus() != GroupStatus.ACTIVE) {
            throw new IllegalStateException("Group is not ACTIVE");
        }
    }

    private boolean isExpired(Invitation inv) {
        return inv.getExpiresAt() != null && LocalDateTime.now().isAfter(inv.getExpiresAt());
    }

    private String genToken() {
        // token ngẫu nhiên đủ dài
        return UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
    }

    private String genOtp() {
        // OTP 6 chữ số
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

    private String buildAcceptUrl(String token) {
        // TODO: đọc từ cấu hình FE base url (ví dụ app.frontend-base-url), tạm hard-code
        return "http//localhost:3000/invitations/accept?token=" + token;
    }

    private InvitationResponse toDto(Invitation i) {
        return new InvitationResponse(
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

    @Transactional
    public Page<InvitationResponse> listByGroup(Long groupId, int page, int size, Authentication auth) {
        validateListPermission(groupId, auth);

        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return invitationRepo.findByGroup_GroupId(groupId, pageable)
                .map(this::toDto); // map trực tiếp trên Page
    }

    public InvitationResponse getOne(Long invitationId, Authentication auth) {
        var inv = invitationRepo.findById(invitationId)
                .orElseThrow(() -> new EntityNotFoundException("Invitation not found"));

        validateViewPermission(inv, auth);

        return toDto(inv);
    }

    // ===================== HELPER METHODS FOR AUTHORIZATION =====================
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

        if (!(isGroupAdmin || isStaffAdmin)) {
            throw new AccessDeniedException("Forbidden");
        }
    }

    private void validateInvitationPermission(Invitation inv, Authentication auth) {
        User actor = getCurrentUser(auth);
        boolean hasPermission = isInviterForInvitation(inv, actor)
                || isGroupAdminForInvitation(inv, actor)
                || isStaffOrAdmin(actor);

        if (!hasPermission) {
            throw new AccessDeniedException("Forbidden");
        }
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

    /**
     * Kiểm tra user có đầy đủ giấy tờ đã được duyệt không
     */
    private void validateUserDocuments(Long userId) {
        // Lấy tất cả documents của user
        var documents = userDocumentRepository.findByUserId(userId);
        
        // Kiểm tra có đủ 2 loại giấy tờ: CITIZEN_ID và DRIVER_LICENSE
        boolean hasCitizenId = documents.stream()
                .anyMatch(doc -> "CITIZEN_ID".equals(doc.getDocumentType()) && "APPROVED".equals(doc.getStatus()));
        
        boolean hasDriverLicense = documents.stream()
                .anyMatch(doc -> "DRIVER_LICENSE".equals(doc.getDocumentType()) && "APPROVED".equals(doc.getStatus()));
        
        if (!hasCitizenId) {
            throw new IllegalStateException("User must have approved Citizen ID document to join group");
        }
        
        if (!hasDriverLicense) {
            throw new IllegalStateException("User must have approved Driver License document to join group");
        }
    }

}
