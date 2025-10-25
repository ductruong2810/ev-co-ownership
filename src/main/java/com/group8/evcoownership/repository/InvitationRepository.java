package com.group8.evcoownership.repository;

import com.group8.evcoownership.entity.Invitation;
import com.group8.evcoownership.enums.InvitationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface InvitationRepository extends JpaRepository<Invitation, Long> {

    // ===================== BASIC FINDERS =====================

    /** Tìm theo token để xác thực lời mời */
    Optional<Invitation> findByToken(String token);

    /** Tìm theo mã OTP và trạng thái */
    Optional<Invitation> findByOtpCodeAndStatus(String otpCode, InvitationStatus status);

    /** Lấy danh sách lời mời theo group (phân trang) */
    Page<Invitation> findByGroup_GroupId(Long groupId, Pageable pageable);

    // ===================== EXISTENCE / DUPLICATE CHECK =====================

    /** Kiểm tra đã tồn tại invitation với email & trạng thái nhất định trong group chưa */
    boolean existsByGroup_GroupIdAndInviteeEmailIgnoreCaseAndStatus(
            Long groupId,
            String inviteeEmail,
            InvitationStatus status
    );

    /** Tìm invitation theo group + email + trạng thái (dùng cho resend trong service.create) */
    Optional<Invitation> findByGroup_GroupIdAndInviteeEmailIgnoreCaseAndStatus(
            Long groupId,
            String inviteeEmail,
            InvitationStatus status
    );

    // ===================== MAINTENANCE HELPERS =====================

    /** Đếm số invitation ở trạng thái nào đó đã hết hạn */
    long countByStatusAndExpiresAtBefore(
            InvitationStatus status,
            LocalDateTime time
    );
}
