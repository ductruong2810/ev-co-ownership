package com.group8.evcoownership.service;

import com.group8.evcoownership.dto.*;
import com.group8.evcoownership.entity.OwnershipGroup;
import com.group8.evcoownership.entity.UsageBooking;
import com.group8.evcoownership.entity.User;
import com.group8.evcoownership.entity.UserDocument;
import com.group8.evcoownership.enums.RoleName;
import com.group8.evcoownership.enums.UserStatus;
import com.group8.evcoownership.exception.ResourceNotFoundException;
import com.group8.evcoownership.repository.OwnershipGroupRepository;
import com.group8.evcoownership.repository.UsageBookingRepository;
import com.group8.evcoownership.repository.UserDocumentRepository;
import com.group8.evcoownership.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;



@Service
@Slf4j
public class StaffService {

    @Autowired
    private UserRepository userRepository; // làm việc với bảng Users

    @Autowired
    private UserDocumentRepository userDocumentRepository; // tương tự như ở trên

    @Autowired
    private UserProfileService userProfileService; // Service dựng UserProfileResponseDTO từ nhiều nguồn

    @Autowired
    private OwnershipGroupRepository ownershipGroupRepository; // làm việc với bảng OwnershipGroup

    @Autowired
    private UsageBookingRepository usageBookingRepository; // làm việc với bảng UsageBooking


    // ========= Lấy ds CO_OWNER và lọc theo status/document =========
    public List<UserProfileResponseDTO> getAllUsers(String status, String documentStatus) {
        // Lấy tất cả user có role CO_OWNER
        List<User> users = userRepository.findByRoleRoleName(RoleName.CO_OWNER);



        // Nếu có truyền status (ACTIVE, BANNED, PENDING), filter thêm theo trạng thái use
        if (status != null) {
            UserStatus userStatus = UserStatus.valueOf(status.toUpperCase());
            users = users.stream()
                    .filter(user -> user.getStatus() == userStatus)
                    .toList();
        }

        // nếu có truyền documentStatus, map sang profile
        // rồi filter theo trạng thái tài liệu
        if (documentStatus != null) {
            return users.stream()
                    .map(user -> userProfileService.getUserProfile(user.getEmail()))
                    .filter(profile -> hasDocumentWithStatus(profile, documentStatus))
                    .collect(Collectors.toList());
        }

        // Nếu không lọc theo documentStatus thì
        // chỉ map tất cả sang UserProfileResponseDTO
        return users.stream()
                .map(user -> userProfileService.getUserProfile(user.getEmail()))
                .collect(Collectors.toList());
    }

    // ========= Lấy chi tiết 1 user =========
    public UserProfileResponseDTO getUserDetail(Long userId) {
        // Dùng UserProfileService để dựng profile đầy đủ theo userId
        return userProfileService.getUserProfileById(userId);
    }

    // ========= Lấy user có document đang pending =========
    public List<UserProfileResponseDTO> getUsersWithPendingDocuments() {
        // Lấy tất cả co-owner
        List<User> allUsers = userRepository.findByRoleRoleName(RoleName.CO_OWNER);

        // Map sang profile và giữ lại những user có tài liệu trạng thái PENDING
        return allUsers.stream()
                .map(user -> userProfileService.getUserProfile(user.getEmail()))
                .filter(profile -> hasDocumentWithStatus(profile, "PENDING"))
                .collect(Collectors.toList());
    }


    // ========= Lấy Ds group + booking của 1 user =========
    public List<GroupBookingDTO> getGroupsByUserId(Long userId) {
        // Tìm tất cả group mà user này là thành viên
        List<OwnershipGroup> groups = ownershipGroupRepository.findByMembersUserId(userId);

        // Với mỗi group, lấy danh sách booking và convert sang DTO chứa QR code
        return groups.stream()
                .map(group -> {
                    // Lấy toàn bộ booking thuộc group
                    List<UsageBooking> bookings = usageBookingRepository.findAllBookingsByGroupId(group.getGroupId());

                    // Sắp xếp booking theo id, rồi map sang BookingQRCodeDTO
                    List<BookingQRCodeDTO> bookingDTOs = bookings.stream()
                            .sorted((b1, b2) -> Long.compare(b1.getId(), b2.getId()))
                            .map(this::mapBookingToQRCodeDTO)
                            .toList();

                    // Gói lại thành GroupBookingDTO gồm group info va list booking
                    return new GroupBookingDTO(
                            group.getGroupId(),
                            group.getGroupName(),
                            bookingDTOs
                    );
                })
                .toList();
    }

    // ========= Lấy Qr code của tất cả CO_OWNER  =========
    public Page<UserGroupBookingsResponseDTO> getAllUsersQRCode(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);

        // Lấy danh sách co-owner theo trang
        Page<User> userPage = userRepository.findByRoleRoleName(RoleName.CO_OWNER, pageable);

        // Với mỗi user, lấy group + booking + QR code
        List<UserGroupBookingsResponseDTO> usersWithQRCodes = userPage.getContent().stream()
                .map(user -> {
                    // Lấy các group mà user này thuộc về
                    List<OwnershipGroup> groups = ownershipGroupRepository.findByMembersUserId(user.getUserId());

                    // Với mỗi group, lấy booking và map sang DTO
                    List<GroupBookingDTO> groupBookings = groups.stream()
                            .map(group -> {
                                List<UsageBooking> bookings = usageBookingRepository.findAllBookingsByGroupId(group.getGroupId());

                                List<BookingQRCodeDTO> bookingDTOs = bookings.stream()
                                        .sorted((b1, b2) -> Long.compare(b1.getId(), b2.getId()))
                                        .map(this::mapBookingToQRCodeDTO)
                                        .toList();

                                return new GroupBookingDTO(
                                        group.getGroupId(),
                                        group.getGroupName(),
                                        bookingDTOs
                                );
                            })
                            .toList();

                    // Trả về DTO tổng hợp gồm user + list group + list booking/QR
                    return new UserGroupBookingsResponseDTO(
                            user.getUserId(),
                            user.getFullName(),
                            groupBookings
                    );
                })
                // Chỉ giữ những user thực sự có group (loại user chưa join group nào)
                .filter(user -> !user.getGroups().isEmpty())
                .toList();

        // Bọc lại thành Page để giữ được thông tin phân trang
        return new PageImpl<>(usersWithQRCodes, pageable, userPage.getTotalElements());
    }


    // ========= Helper map booking -> Qr DTO =========
    private BookingQRCodeDTO mapBookingToQRCodeDTO(UsageBooking booking) {
        // Convert entity UsageBooking sang DTO chứa id + QR checkin/checkout + thời gian dạng String
        return new BookingQRCodeDTO(
                booking.getId(),
                booking.getQrCodeCheckin(),   // QR checkin của booking
                booking.getQrCodeCheckout(),  // QR checkout của booking
                booking.getStartDateTime() != null ? booking.getStartDateTime().toString() : null,
                booking.getEndDateTime() != null ? booking.getEndDateTime().toString() : null,
                booking.getCreatedAt() != null ? booking.getCreatedAt().toString() : null
        );
    }


    // ========= Review document APPROVE / REJECT =========
    @Transactional
    public String reviewDocument(Long documentId, ReviewDocumentRequestDTO request, String staffEmail) {
        // Tìm document theo id, nếu không có thì ném lỗi
        UserDocument document = userDocumentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found"));

        // Tìm staff theo email (lấy từ JWT trước đó)
        User staff = userRepository.findByEmail(staffEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Staff not found"));

        // Chuẩn hóa action sang upper để so sánh
        String action = request.getAction().toUpperCase();

        // Xử lý theo action: APPROVE hoặc REJECT
        if ("APPROVE".equals(action)) {
            document.setStatus("APPROVED");
            document.setReviewNote("Document verified and approved");
        } else if ("REJECT".equals(action)) {
            document.setStatus("REJECTED");
            // Ở đây đang để note cứng
            document.setReviewNote("Document rejected");
        } else {
            throw new IllegalArgumentException("Invalid action. Use APPROVE or REJECT");
        }

        // Gán người review là staff hiện tại
        document.setReviewedBy(staff);
        userDocumentRepository.save(document);

        // Trả message cho client
        return String.format("Document %s successfully", action.toLowerCase());
    }

    // ========= Phê duyệt document  =========
    @Transactional
    public String approveDocument(Long documentId, String staffEmail) {
        UserDocument document = userDocumentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found"));

        User staff = userRepository.findByEmail(staffEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Staff not found"));

        // Set trạng thái APPROVED và ghi note mặc định
        document.setStatus("APPROVED");
        document.setReviewNote("Document verified and approved");
        document.setReviewedBy(staff);
        userDocumentRepository.save(document);

        log.info("Document {} approved by staff {}", documentId, staffEmail);
        return "Document approved successfully";
    }

    // ========= Từ chối document =========
    @Transactional
    public String rejectDocument(Long documentId, String reason, String staffEmail) {
        // Bắt buộc phải có reason khi reject
        if (reason == null || reason.trim().isEmpty()) {
            throw new IllegalArgumentException("Rejection reason is required");
        }

        UserDocument document = userDocumentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found"));

        User staff = userRepository.findByEmail(staffEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Staff not found"));

        // Set trạng thái REJECTED và lưu lại lý do cụ thể
        document.setStatus("REJECTED");
        document.setReviewNote(reason);
        document.setReviewedBy(staff);
        userDocumentRepository.save(document);

        log.info("Document {} rejected by staff {}: {}", documentId, staffEmail, reason);
        return "Document rejected successfully";
    }

    @Transactional
    public String deleteUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        user.setStatus(UserStatus.BANNED);
        userRepository.save(user);
        return "User has been deactivated (BANNED) successfully";
    }


    // ========= Helper check document status trong profile =========
    private boolean hasDocumentWithStatus(UserProfileResponseDTO profile, String status) {
        if (profile.getDocuments() == null) return false;

        // Kiểm tra CCCD (mặt trước / sau) có document nào có status khớp không
        boolean hasCitizenId = profile.getDocuments().getCitizenIdImages() != null &&
                (hasStatus(profile.getDocuments().getCitizenIdImages().getFront(), status) ||
                        hasStatus(profile.getDocuments().getCitizenIdImages().getBack(), status));

        // Kiểm tra GPLX (mặt trước / sau) tương tự
        boolean hasDriverLicense = profile.getDocuments().getDriverLicenseImages() != null &&
                (hasStatus(profile.getDocuments().getDriverLicenseImages().getFront(), status) ||
                        hasStatus(profile.getDocuments().getDriverLicenseImages().getBack(), status));

        return hasCitizenId || hasDriverLicense;
    }

    // Check 1 document đơn lẻ có status mong muốn hay không
    private boolean hasStatus(DocumentDetailDTO doc, String status) {
        return doc != null && status.equalsIgnoreCase(doc.getStatus());
    }
}
