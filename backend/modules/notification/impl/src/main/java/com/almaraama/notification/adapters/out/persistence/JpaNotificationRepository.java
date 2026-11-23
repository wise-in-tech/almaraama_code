package com.almaraama.notification.adapters.out.persistence;

import com.almaraama.notification.domain.Notification;
import com.almaraama.notification.domain.NotificationRepository;
import com.almaraama.shared.id.PersonId;
import com.almaraama.shared.paging.PageQuery;
import com.almaraama.shared.paging.PageResult;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

/** Adapter from the domain's out port to Spring Data JPA, in schema {@code notification} only (CF-3). */
@Component
class JpaNotificationRepository implements NotificationRepository {

    private final SpringDataNotifications notifications;

    /** @param notifications Spring Data repository of the notification table */
    JpaNotificationRepository(SpringDataNotifications notifications) {
        this.notifications = notifications;
    }

    /** Loads a notification. */
    @Override
    public Optional<Notification> findById(UUID id) {
        return notifications.findById(id);
    }

    /** Whether a notification was already created for this event and recipient. */
    @Override
    public boolean exists(String sourceKey) {
        return notifications.existsBySourceKey(sourceKey);
    }

    /** A recipient's notifications, newest first. */
    @Override
    public PageResult<Notification> ofRecipient(PersonId recipient, boolean unreadOnly, PageQuery page) {
        PageRequest request = PageRequest.of(page.page(), page.size());
        Page<Notification> found = unreadOnly
                ? notifications.findByRecipientIdAndReadAtIsNullOrderByCreatedAtDesc(recipient.value(), request)
                : notifications.findByRecipientIdOrderByCreatedAtDesc(recipient.value(), request);
        return new PageResult<>(found.getContent(), found.getNumber(), found.getSize(), found.getTotalElements());
    }

    /** Number of unread notifications of a recipient. */
    @Override
    public long countUnread(PersonId recipient) {
        return notifications.countByRecipientIdAndReadAtIsNull(recipient.value());
    }

    /** Every unread notification of a recipient. */
    @Override
    public List<Notification> unreadOf(PersonId recipient) {
        return notifications.findByRecipientIdAndReadAtIsNull(recipient.value());
    }

    /** Inserts or updates a notification. */
    @Override
    public Notification save(Notification notification) {
        return notifications.save(notification);
    }
}
