package com.group8.evcoownership.repository;

import com.group8.evcoownership.entity.Notification;
import com.group8.evcoownership.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Page<Notification> findByUser(User user, Pageable pageable);

    Page<Notification> findByUserAndIsRead(User user, Boolean isRead, Pageable pageable);

    List<Notification> findByUserAndIsRead(User user, Boolean isRead);

    @Query("SELECT n FROM Notification n WHERE n.user = :user ORDER BY n.createdAt DESC")
    List<Notification> findByUserOrderByCreatedAtDesc(@Param("user") User user);

    @Query("SELECT n FROM Notification n WHERE n.user = :user AND n.isRead = :isRead ORDER BY n.createdAt DESC")
    List<Notification> findByUserAndIsReadOrderByCreatedAtDesc(@Param("user") User user, @Param("isRead") Boolean isRead);

    long countByUserAndIsRead(User user, Boolean isRead);
}
