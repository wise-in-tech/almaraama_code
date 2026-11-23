package com.almaraama.notification.application;

import com.almaraama.notification.domain.Notification;
import com.almaraama.notification.domain.NotificationRepository;
import com.almaraama.notification.domain.NotificationTemplate;
import com.almaraama.shared.error.BusinessException;
import com.almaraama.shared.error.ErrorCategory;
import com.almaraama.shared.id.PersonId;
import com.almaraama.shared.id.SiteId;
import com.almaraama.shared.paging.PageQuery;
import com.almaraama.shared.paging.PageResult;
import com.almaraama.shared.perimeter.Perimeter;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** In-application notifications (K1): written by event listeners, read by their recipient only (M3). */
@Service
public class Notifications {

    private final NotificationRepository notifications;
    private final Clock clock;

    /**
     * @param notifications the notification table
     * @param clock time of creation and reading
     */
    Notifications(NotificationRepository notifications, Clock clock) {
        this.notifications = notifications;
        this.clock = clock;
    }

    /** Idempotent: the same source key never creates a second notification. */
    @Transactional
    public void notify(PersonId recipient, NotificationTemplate template, Map<String, String> params, String link,
                       SiteId siteId, String sourceKey) {
        if (!notifications.exists(sourceKey)) {
            notifications.save(Notification.of(recipient, template, params, link, siteId, sourceKey,
                    clock.instant()));
        }
    }

    /** The caller's notifications. */
    @Transactional(readOnly = true)
    public PageResult<Notification> mine(Perimeter perimeter, boolean unreadOnly, PageQuery page) {
        return notifications.ofRecipient(me(perimeter), unreadOnly, page);
    }

    /** The caller's unread count. */
    @Transactional(readOnly = true)
    public long unreadCount(Perimeter perimeter) {
        return notifications.countUnread(me(perimeter));
    }

    /** Marks one of the caller's notifications as read; {@code NOT_FOUND} for anyone else's. */
    @Transactional
    public void markRead(Perimeter perimeter, UUID id) {
        Notification notification = notifications.findById(id)
                .filter(n -> n.recipientId().equals(me(perimeter)))
                .orElseThrow(() -> new BusinessException("NOT_FOUND", ErrorCategory.NOT_FOUND, "Not found"));
        notification.markRead(clock.instant());
        notifications.save(notification);
    }

    /** Marks all the caller's notifications as read. */
    @Transactional
    public void markAllRead(Perimeter perimeter) {
        notifications.unreadOf(me(perimeter)).forEach(n -> {
            n.markRead(clock.instant());
            notifications.save(n);
        });
    }

    /** The caller, recipient of their notifications. */
    private static PersonId me(Perimeter perimeter) {
        return new PersonId(perimeter.actor().id());
    }
}
