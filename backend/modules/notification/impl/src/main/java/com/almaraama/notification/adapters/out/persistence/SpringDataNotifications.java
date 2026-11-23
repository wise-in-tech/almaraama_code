package com.almaraama.notification.adapters.out.persistence;

import com.almaraama.notification.domain.Notification;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.Repository;

/** Notifications, schema notification. No delete (M2). */
interface SpringDataNotifications extends Repository<Notification, UUID> {

    /** Loads a notification. */
    Optional<Notification> findById(UUID id);

    /** Whether a notification exists for this event and recipient. */
    boolean existsBySourceKey(String sourceKey);

    /** A recipient's notifications, newest first. */
    Page<Notification> findByRecipientIdOrderByCreatedAtDesc(UUID recipientId, Pageable page);

    /** A recipient's unread notifications, newest first. */
    Page<Notification> findByRecipientIdAndReadAtIsNullOrderByCreatedAtDesc(UUID recipientId, Pageable page);

    /** Number of unread notifications of a recipient. */
    long countByRecipientIdAndReadAtIsNull(UUID recipientId);

    /** Every unread notification of a recipient. */
    List<Notification> findByRecipientIdAndReadAtIsNull(UUID recipientId);

    /** Inserts or updates a notification. */
    Notification save(Notification notification);
}
