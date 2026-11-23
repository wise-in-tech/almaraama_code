package com.almaraama.notification.domain;

import com.almaraama.shared.id.PersonId;
import com.almaraama.shared.paging.PageQuery;
import com.almaraama.shared.paging.PageResult;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Out port: notifications. No delete (M2). */
public interface NotificationRepository {

    /** Loads a notification. */
    Optional<Notification> findById(UUID id);

    /** Whether a notification was already created for this event and recipient (idempotent listeners). */
    boolean exists(String sourceKey);

    /** A recipient's notifications, newest first, optionally unread only. */
    PageResult<Notification> ofRecipient(PersonId recipient, boolean unreadOnly, PageQuery page);

    /** Number of unread notifications of a recipient. */
    long countUnread(PersonId recipient);

    /** Every unread notification of a recipient. */
    List<Notification> unreadOf(PersonId recipient);

    /** Stores a notification. */
    Notification save(Notification notification);
}
