package com.group8.evcoownership.service;

import com.group8.evcoownership.entity.UserDocument;
import com.group8.evcoownership.repository.UserDocumentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserDocumentValidationService {

    private final UserDocumentRepository userDocumentRepository;

    /**
     * Kiểm tra user có đầy đủ giấy tờ đã được duyệt không
     * @param userId ID của user cần kiểm tra
     * @throws IllegalStateException nếu user không có đủ giấy tờ đã được duyệt
     */
    public void validateUserDocuments(Long userId) {
        // Lấy tất cả documents của user
        List<UserDocument> documents = userDocumentRepository.findByUserId(userId);
        
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

    /**
     * Kiểm tra user có giấy tờ cụ thể đã được duyệt không
     * @param userId ID của user
     * @param documentType Loại giấy tờ (CITIZEN_ID, DRIVER_LICENSE)
     * @return true nếu có giấy tờ đã được duyệt
     */
    public boolean hasApprovedDocument(Long userId, String documentType) {
        List<UserDocument> documents = userDocumentRepository.findByUserId(userId);
        return documents.stream()
                .anyMatch(doc -> documentType.equals(doc.getDocumentType()) && "APPROVED".equals(doc.getStatus()));
    }

    /**
     * Lấy danh sách giấy tờ đã được duyệt của user
     * @param userId ID của user
     * @return List các loại giấy tờ đã được duyệt
     */
    public List<String> getApprovedDocumentTypes(Long userId) {
        List<UserDocument> documents = userDocumentRepository.findByUserId(userId);
        return documents.stream()
                .filter(doc -> "APPROVED".equals(doc.getStatus()))
                .map(UserDocument::getDocumentType)
                .distinct()
                .toList();
    }
}
