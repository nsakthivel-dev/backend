package com.evplatform.repository;

import com.evplatform.domain.Notification;
import com.evplatform.domain.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    @Query("SELECT n FROM Notification n WHERE n.recipientUser.id = :userId " +
           "OR (n.recipientRole IS NOT NULL AND n.recipientRole = :role) " +
           "ORDER BY n.createdAt DESC")
    List<Notification> findForUserOrRole(@Param("userId") Long userId, @Param("role") Role role);

    List<Notification> findTop50ByOrderByCreatedAtDesc();
}
