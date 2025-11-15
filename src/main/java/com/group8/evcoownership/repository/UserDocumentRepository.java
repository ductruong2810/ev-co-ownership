package com.group8.evcoownership.repository;

import com.group8.evcoownership.entity.UserDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserDocumentRepository extends JpaRepository<UserDocument, Long> {

    // Tìm tất cả document của user
    List<UserDocument> findByUserId(Long userId);

    @Query("""
            SELECT ud
            FROM UserDocument ud
            LEFT JOIN FETCH ud.reviewedBy
            WHERE ud.userId = :userId
            """)
    List<UserDocument> findAllWithReviewerByUserId(@Param("userId") Long userId);

    // Tìm document theo user, type và side
    Optional<UserDocument> findByUserIdAndDocumentTypeAndSide(Long userId, String documentType, String side);

    // Tìm tất cả document theo type của user
    List<UserDocument> findByUserIdAndDocumentType(Long userId, String documentType);

    // Kiểm tra user đã có document type + side chưa
    boolean existsByUserIdAndDocumentTypeAndSide(Long userId, String documentType, String side);

    Optional<UserDocument> findByDocumentNumber(String documentNumber);


}
