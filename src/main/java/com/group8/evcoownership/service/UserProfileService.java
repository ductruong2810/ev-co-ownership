package com.group8.evcoownership.service;

import com.group8.evcoownership.dto.UserProfileResponseDTO;
import com.group8.evcoownership.entity.User;
import com.group8.evcoownership.entity.UserDocument;
import com.group8.evcoownership.exception.ResourceNotFoundException;
import com.group8.evcoownership.repository.UserDocumentRepository;
import com.group8.evcoownership.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class UserProfileService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserDocumentRepository userDocumentRepository;

    /**
     * Lấy profile của user đang login (dùng email từ token)
     */
    public UserProfileResponseDTO getUserProfile(String email) {
        log.info("Fetching profile for user: {}", email);

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng"));

        return buildProfileResponse(user);
    }

    /**
     * Lấy profile theo userId (public - không cần token)
     */
    public UserProfileResponseDTO getUserProfileById(Long userId) {
        log.info("Fetching public profile for userId: {}", userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        String.format("Không tìm thấy người dùng với ID: %d", userId)
                ));

        return buildProfileResponse(user);
    }

    /**
     * Build response từ User entity (dùng chung cho cả 2 method)
     */
    private UserProfileResponseDTO buildProfileResponse(User user) {
        // Lấy tất cả documents của user
        List<UserDocument> allDocuments = userDocumentRepository.findByUserId(user.getUserId());

        // Build CCCD
        UserProfileResponseDTO.DocumentTypeDTO citizenId = buildDocumentType(allDocuments, "CITIZEN_ID");

        // Build GPLX
        UserProfileResponseDTO.DocumentTypeDTO driverLicense = buildDocumentType(allDocuments, "DRIVER_LICENSE");

        // Build response
        return UserProfileResponseDTO.builder()
                .userId(user.getUserId())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .phoneNumber(user.getPhoneNumber())
                .avatarUrl(user.getAvatarUrl())
                .roleName(user.getRole().getRoleName().name())
                .status(user.getStatus().name())
                .createdAt(user.getCreatedAt())
                .documents(UserProfileResponseDTO.DocumentsDTO.builder()
                        .citizenIdImages(citizenId)
                        .driverLicenseImages(driverLicense)
                        .build())
                .statistics(UserProfileResponseDTO.StatisticsDTO.builder()
                        .groupsJoined(getGroupsCount(user.getUserId()))
                        .accountStatus(user.getStatus().name())
                        .memberSince(user.getCreatedAt())
                        .build())
                .build();
    }

    /**
     * Build DocumentTypeDTO (FRONT + BACK + Details)
     */
    private UserProfileResponseDTO.DocumentTypeDTO buildDocumentType(
            List<UserDocument> allDocuments,
            String documentType) {

        // Filter documents by type
        List<UserDocument> typeDocs = allDocuments.stream()
                .filter(doc -> documentType.equals(doc.getDocumentType()))
                .collect(Collectors.toList());

        // Quick access URLs
        String frontUrl = "";
        String backUrl = "";

        // Build detailed info
        List<UserProfileResponseDTO.DocumentDetailDTO> details = typeDocs.stream()
                .map(doc -> UserProfileResponseDTO.DocumentDetailDTO.builder()
                        .documentId(doc.getDocumentId())
                        .side(doc.getSide())
                        .imageUrl(doc.getImageUrl())
                        .status(doc.getStatus())
                        .reviewNote(doc.getReviewNote())
                        .uploadedAt(doc.getCreatedAt())
                        .build())
                .collect(Collectors.toList());

        // Extract URLs from details for quick access
        for (UserProfileResponseDTO.DocumentDetailDTO detail : details) {
            if ("FRONT".equals(detail.getSide())) {
                frontUrl = detail.getImageUrl() != null ? detail.getImageUrl() : "";
            } else if ("BACK".equals(detail.getSide())) {
                backUrl = detail.getImageUrl() != null ? detail.getImageUrl() : "";
            }
        }

        return UserProfileResponseDTO.DocumentTypeDTO.builder()
                .front(frontUrl)
                .back(backUrl)
                .details(details)
                .build();
    }

    /**
     * Đếm số nhóm user đã tham gia
     * TODO: Implement khi có bảng GroupMembers
     */
    private int getGroupsCount(Long userId) {
        return 0;
    }
}
