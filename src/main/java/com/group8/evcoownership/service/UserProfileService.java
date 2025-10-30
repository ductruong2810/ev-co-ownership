package com.group8.evcoownership.service;

import com.group8.evcoownership.dto.UserProfileResponseDTO;
import com.group8.evcoownership.entity.User;
import com.group8.evcoownership.entity.UserDocument;
import com.group8.evcoownership.exception.ResourceNotFoundException;
import com.group8.evcoownership.repository.OwnershipShareRepository;
import com.group8.evcoownership.repository.UserDocumentRepository;
import com.group8.evcoownership.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
public class UserProfileService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserDocumentRepository userDocumentRepository;

    @Autowired
    private OwnershipShareRepository ownershipShareRepository;


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

    private UserProfileResponseDTO buildProfileResponse(User user) {
        List<UserDocument> allDocuments = userDocumentRepository.findByUserId(user.getUserId());

        UserProfileResponseDTO.DocumentTypeDTO citizenId = buildDocumentType(allDocuments, "CITIZEN_ID");
        UserProfileResponseDTO.DocumentTypeDTO driverLicense = buildDocumentType(allDocuments, "DRIVER_LICENSE");

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

    private UserProfileResponseDTO.DocumentTypeDTO buildDocumentType(
            List<UserDocument> allDocuments,
            String documentType) {

        List<UserDocument> typeDocs = allDocuments.stream()
                .filter(doc -> documentType.equals(doc.getDocumentType()))
                .toList();

        UserProfileResponseDTO.DocumentDetailDTO frontDetail = null;
        UserProfileResponseDTO.DocumentDetailDTO backDetail = null;

        for (UserDocument doc : typeDocs) {
            UserProfileResponseDTO.DocumentDetailDTO detail = UserProfileResponseDTO.DocumentDetailDTO.builder()
                    .documentId(doc.getDocumentId())
                    .imageUrl(doc.getImageUrl())
                    .status(doc.getStatus())
                    // ← KHÔNG map reviewNote
                    .uploadedAt(doc.getCreatedAt())
                    .build();

            if ("FRONT".equals(doc.getSide())) {
                frontDetail = detail;
            } else if ("BACK".equals(doc.getSide())) {
                backDetail = detail;
            }
        }

        return UserProfileResponseDTO.DocumentTypeDTO.builder()
                .front(frontDetail)
                .back(backDetail)
                .build();
    }

    private int getGroupsCount(Long userId) {
        return ownershipShareRepository.countByUser_UserId(userId).intValue();
    }
}
