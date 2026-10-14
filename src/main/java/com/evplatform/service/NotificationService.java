package com.evplatform.service;

import com.evplatform.domain.Notification;
import com.evplatform.domain.Role;
import com.evplatform.domain.User;
import com.evplatform.repository.NotificationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;

    public NotificationService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @Transactional
    public Notification sendNotification(User recipientUser, Role recipientRole, String title, String message, String severity, Long relatedEntityId) {
        Notification notification = new Notification(recipientUser, recipientRole, title, message, severity, relatedEntityId);
        return notificationRepository.save(notification);
    }

    public List<Notification> getNotificationsForUser(Long userId, Role role) {
        return notificationRepository.findForUserOrRole(userId, role);
    }

    public List<Notification> getRecentNotifications() {
        return notificationRepository.findTop50ByOrderByCreatedAtDesc();
    }

    @Transactional
    public void markAsRead(Long notificationId) {
        notificationRepository.findById(notificationId).ifPresent(n -> {
            n.setIsRead(true);
            notificationRepository.save(n);
        });
    }
}
