package com.group8.evcoownership.service;

import com.group8.evcoownership.dto.ReviewDocumentRequestDTO;
import com.group8.evcoownership.dto.UserProfileResponseDTO;
import com.group8.evcoownership.entity.User;
import com.group8.evcoownership.entity.UserDocument;
import com.group8.evcoownership.enums.RoleName;
import com.group8.evcoownership.enums.UserStatus;
import com.group8.evcoownership.exception.ResourceNotFoundException;
import com.group8.evcoownership.repository.UserDocumentRepository;
import com.group8.evcoownership.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class StaffService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserDocumentRepository userDocumentRepository;

    @Autowired
    private UserProfileService userProfileService;

    public List<UserProfileResponseDTO> getAllUsers(String status, String documentStatus) {
        List<User> users = userRepository.findByRoleRoleName(RoleName.CO_OWNER);

        if (status != null) {
            UserStatus userStatus = UserStatus.valueOf(status.toUpperCase());
            users = users.stream()
                    .filter(user -> user.getStatus() == userStatus)
                    .collect(Collectors.toList());
        }

        if (documentStatus != null) {
            return users.stream()
                    .map(user -> userProfileService.getUserProfile(user.getEmail()))
                    .filter(profile -> hasDocumentWithStatus(profile, documentStatus))
                    .collect(Collectors.toList());
        }

        return users.stream()
                .map(user -> userProfileService.getUserProfile(user.getEmail()))
                .collect(Collectors.toList());
    }

    public UserProfileResponseDTO getUserDetail(Long userId) {
        return userProfileService.getUserProfileById(userId);
    }

    public List<UserProfileResponseDTO> getUsersWithPendingDocuments() {
        List<User> allUsers = userRepository.findByRoleRoleName(RoleName.CO_OWNER);

        return allUsers.stream()
                .map(user -> userProfileService.getUserProfile(user.getEmail()))
                .filter(profile -> hasDocumentWithStatus(profile, "PENDING"))
                .collect(Collectors.toList());
    }

    @Transactional
    public String reviewDocument(Long documentId, ReviewDocumentRequestDTO request, String staffEmail) {
        UserDocument document = userDocumentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found"));

        User staff = userRepository.findByEmail(staffEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Staff not found"));

        String action = request.getAction().toUpperCase();

        // ← AUTO-GENERATE reviewNote
        if ("APPROVE".equals(action)) {
            document.setStatus("APPROVED");
            document.setReviewNote("Document verified and approved");
        } else if ("REJECT".equals(action)) {
            document.setStatus("REJECTED");
            document.setReviewNote("Document rejected");
        } else {
            throw new IllegalArgumentException("Invalid action. Use APPROVE or REJECT");
        }

        document.setReviewedBy(staff);
        userDocumentRepository.save(document);

        log.info("Document {} {} by staff {}", documentId, action, staffEmail);
        return String.format("Document %s successfully", action.toLowerCase());
    }

    @Transactional
    public String approveDocument(Long documentId, String staffEmail) {
        UserDocument document = userDocumentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found"));

        User staff = userRepository.findByEmail(staffEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Staff not found"));

        document.setStatus("APPROVED");
        document.setReviewNote("Document verified and approved");
        document.setReviewedBy(staff);
        userDocumentRepository.save(document);

        log.info("Document {} approved by staff {}", documentId, staffEmail);
        return "Document approved successfully";
    }

    @Transactional
    public String rejectDocument(Long documentId, String reason, String staffEmail) {
        if (reason == null || reason.trim().isEmpty()) {
            throw new IllegalArgumentException("Rejection reason is required");
        }

        UserDocument document = userDocumentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found"));

        User staff = userRepository.findByEmail(staffEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Staff not found"));

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

        // Soft delete to avoid FK violations
        user.setStatus(UserStatus.BANNED);
        userRepository.save(user);
        return "User has been deactivated (BANNED) successfully";
    }

    private boolean hasDocumentWithStatus(UserProfileResponseDTO profile, String status) {
        if (profile.getDocuments() == null) return false;

        boolean hasCitizenId = profile.getDocuments().getCitizenIdImages() != null &&
                (hasStatus(profile.getDocuments().getCitizenIdImages().getFront(), status) ||
                        hasStatus(profile.getDocuments().getCitizenIdImages().getBack(), status));

        boolean hasDriverLicense = profile.getDocuments().getDriverLicenseImages() != null &&
                (hasStatus(profile.getDocuments().getDriverLicenseImages().getFront(), status) ||
                        hasStatus(profile.getDocuments().getDriverLicenseImages().getBack(), status));

        return hasCitizenId || hasDriverLicense;
    }

    private boolean hasStatus(UserProfileResponseDTO.DocumentDetailDTO doc, String status) {
        return doc != null && status.equalsIgnoreCase(doc.getStatus());
    }
}
