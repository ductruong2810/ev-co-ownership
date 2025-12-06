package com.group8.evcoownership.service;

import com.group8.evcoownership.dto.*;
import com.group8.evcoownership.entity.User;
import com.group8.evcoownership.entity.UserDocument;
import com.group8.evcoownership.exception.ResourceNotFoundException;
import com.group8.evcoownership.repository.OwnershipShareRepository;
import com.group8.evcoownership.repository.UserDocumentRepository;
import com.group8.evcoownership.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Service
@Slf4j
@Transactional(readOnly = true)
public class UserProfileService {

    private final UserRepository userRepository;

    private final UserDocumentRepository userDocumentRepository;

    private final OwnershipShareRepository ownershipShareRepository;
    private final CloudflareR2StorageService r2StorageService;

    public UserProfileService(UserRepository userRepository,
                              UserDocumentRepository userDocumentRepository,
                              OwnershipShareRepository ownershipShareRepository,
                              CloudflareR2StorageService r2StorageService) {
        this.userRepository = userRepository;
        this.userDocumentRepository = userDocumentRepository;
        this.ownershipShareRepository = ownershipShareRepository;
        this.r2StorageService = r2StorageService;
    }


    public UserProfileResponseDTO getUserProfile(String email) {
        log.info("Fetching profile for user: {}", email);

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        return buildProfileResponse(user);
    }

    public UserProfileResponseDTO getUserProfileById(Long userId) {
        log.info("Fetching public profile for userId: {}", userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        String.format("User not found with ID: %d", userId)
                ));

        return buildProfileResponse(user);
    }

    @Transactional
    public UserProfileResponseDTO updateAvatar(String email, MultipartFile avatarFile) {
        log.info("Updating avatar for user: {}", email);

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        try {
            // Delete old avatar if exists
            if (user.getAvatarUrl() != null && !user.getAvatarUrl().isEmpty()) {
                try {
                    r2StorageService.deleteFile(user.getAvatarUrl());
                } catch (Exception e) {
                    log.warn("Failed to delete old avatar for user {}: {}", email, e.getMessage());
                }
            }

            // Upload new avatar to avatars/ folder
            String newAvatarUrl = r2StorageService.uploadAvatar(avatarFile);
            user.setAvatarUrl(newAvatarUrl);
            userRepository.save(user);

            return buildProfileResponse(user);
        } catch (Exception e) {
            log.error("Failed to update avatar for user {}: {}", email, e.getMessage(), e);
            throw new RuntimeException("Failed to update avatar. Please try again later.");
        }
    }


    private UserProfileResponseDTO buildProfileResponse(User user) {
        List<UserDocument> allDocuments = userDocumentRepository.findAllWithReviewerByUserId(user.getUserId());

        DocumentTypeDTO citizenId = buildDocumentType(allDocuments, "CITIZEN_ID");
        DocumentTypeDTO driverLicense = buildDocumentType(allDocuments, "DRIVER_LICENSE");

        return UserProfileResponseDTO.builder()
                .userId(user.getUserId())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .phoneNumber(user.getPhoneNumber())
                .avatarUrl(user.getAvatarUrl())
                .roleName(user.getRole().getRoleName().name())
                .status(user.getStatus().name())
                .createdAt(user.getCreatedAt())
                .documents(DocumentsDTO.builder()
                        .citizenIdImages(citizenId)
                        .driverLicenseImages(driverLicense)
                        .build())
                .statistics(StatisticsDTO.builder()
                        .groupsJoined(getGroupsCount(user.getUserId()))
                        .accountStatus(user.getStatus().name())
                        .memberSince(user.getCreatedAt())
                        .build())
                .build();
    }

    private DocumentTypeDTO buildDocumentType(
            List<UserDocument> allDocuments,
            String documentType) {

        List<UserDocument> typeDocs = allDocuments.stream()
                .filter(doc -> documentType.equals(doc.getDocumentType()))
                .toList();

        DocumentDetailDTO frontDetail = null;
        DocumentDetailDTO backDetail = null;

        for (UserDocument doc : typeDocs) {
            DocumentDetailDTO detail = DocumentDetailDTO.builder()
                    .documentId(doc.getDocumentId())
                    .imageUrl(doc.getImageUrl())
                    .status(doc.getStatus())
                    .uploadedAt(doc.getCreatedAt())
                    .reviewNote(doc.getReviewNote())
                    .reviewedBy(doc.getReviewedBy() != null ? doc.getReviewedBy().getFullName() : null)
                    // THÊM CÁC TRƯỜNG SAU:
                    .documentNumber(doc.getDocumentNumber())
                    .dateOfBirth(doc.getDateOfBirth())
                    .issueDate(doc.getIssueDate())
                    .expiryDate(doc.getExpiryDate())
                    .address(doc.getAddress())
                    .build();

            if ("FRONT".equals(doc.getSide())) {
                frontDetail = detail;
            } else if ("BACK".equals(doc.getSide())) {
                backDetail = detail;
            }
        }

        return DocumentTypeDTO.builder()
                .front(frontDetail)
                .back(backDetail)
                .build();
    }


    private int getGroupsCount(Long userId) {
        return ownershipShareRepository.countByUser_UserId(userId).intValue();
    }
}
