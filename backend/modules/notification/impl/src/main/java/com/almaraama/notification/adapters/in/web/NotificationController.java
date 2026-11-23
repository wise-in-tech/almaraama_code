package com.almaraama.notification.adapters.in.web;

import com.almaraama.notification.application.Notifications;
import com.almaraama.notification.domain.Notification;
import com.almaraama.notification.domain.NotificationTemplate;
import com.almaraama.shared.paging.PageQuery;
import com.almaraama.shared.paging.PageResult;
import com.almaraama.shared.perimeter.Perimeter;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** The signed-in person's notifications (K1). */
@RestController
class NotificationController {

    /**
     * A notification; the application writes its text from the template and parameters (contract {@code Notification}).
     */
    record NotificationResponse(UUID id, NotificationTemplate template, Map<String, String> params, String link,
                                UUID siteId, boolean read, Instant createdAt) {
    }

    /** Position of a page in the whole result. */
    record PageMeta(int page, int size, long totalElements, int totalPages) {
    }

    /** One page of notifications. */
    record NotificationPage(List<NotificationResponse> items, PageMeta page) {
    }

    /** Number of unread notifications, for the bell. */
    record UnreadCount(long count) {
    }

    private final Notifications notifications;

    /** @param notifications the notification use cases */
    NotificationController(Notifications notifications) {
        this.notifications = notifications;
    }

    /** {@code GET /v1/notifications}: the caller's notifications, newest first. */
    @GetMapping("/v1/notifications")
    NotificationPage list(Perimeter perimeter, @RequestParam(defaultValue = "false") boolean unreadOnly,
                          @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        PageResult<Notification> found = notifications.mine(perimeter, unreadOnly, PageQuery.of(page, size));
        return new NotificationPage(found.items().stream().map(n -> new NotificationResponse(n.id(), n.template(),
                n.params(), n.link().orElse(null), n.siteId().orElse(null), n.read(), n.createdAt())).toList(),
                new PageMeta(found.page(), found.size(), found.totalElements(), found.totalPages()));
    }

    /** {@code GET /v1/notifications/unread-count}. */
    @GetMapping("/v1/notifications/unread-count")
    UnreadCount unread(Perimeter perimeter) {
        return new UnreadCount(notifications.unreadCount(perimeter));
    }

    /** {@code POST /v1/notifications/{id}/read}; {@code NOT_FOUND} for another person's notification. */
    @PostMapping("/v1/notifications/{notificationId}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void read(Perimeter perimeter, @PathVariable UUID notificationId) {
        notifications.markRead(perimeter, notificationId);
    }

    /** {@code POST /v1/notifications/read-all}. */
    @PostMapping("/v1/notifications/read-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void readAll(Perimeter perimeter) {
        notifications.markAllRead(perimeter);
    }
}
