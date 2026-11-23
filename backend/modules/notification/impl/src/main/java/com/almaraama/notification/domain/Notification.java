package com.almaraama.notification.domain;

import com.almaraama.shared.id.PersonId;
import com.almaraama.shared.id.SiteId;
import com.almaraama.shared.id.UuidV7;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * An in-application notification (K1, K6: never SMS nor WhatsApp). {@code sourceKey} makes the listener idempotent: an
 * event received twice creates one notification. Never deleted (M2): it is marked as read.
 */
@Entity
@Table(schema = "notification", name = "notification")
public class Notification {

    @Id
    private UUID id;

    @Column(name = "recipient_id", nullable = false)
    private UUID recipientId;

    @Enumerated(EnumType.STRING)
    @Column(name = "template", nullable = false)
    private NotificationTemplate template;

    @Convert(converter = ParamsConverter.class)
    @Column(name = "params", nullable = false)
    private Map<String, String> params;

    @Column(name = "link")
    private String link;

    @Column(name = "site_id")
    private UUID siteId;

    @Column(name = "source_key", nullable = false, unique = true)
    private String sourceKey;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "read_at")
    private Instant readAt;

    @Version
    private Long version;

    /** For JPA only. */
    protected Notification() {
    }

    /** A new unread notification. */
    public static Notification of(PersonId recipient, NotificationTemplate template, Map<String, String> params,
                                  String link, SiteId siteId, String sourceKey, Instant now) {
        Notification notification = new Notification();
        notification.id = UuidV7.next();
        notification.recipientId = recipient.value();
        notification.template = template;
        notification.params = new LinkedHashMap<>(params);
        notification.link = link;
        notification.siteId = siteId == null ? null : siteId.value();
        notification.sourceKey = sourceKey;
        notification.createdAt = now;
        return notification;
    }

    /** Records the first reading; later calls change nothing. */
    public void markRead(Instant now) {
        if (readAt == null) {
            readAt = now;
        }
    }

    /** Identifier of the notification. */
    public UUID id() {
        return id;
    }

    /** Who receives it. */
    public PersonId recipientId() {
        return new PersonId(recipientId);
    }

    /** What happened. */
    public NotificationTemplate template() {
        return template;
    }

    /** Values for the text, never an amount (J3). */
    public Map<String, String> params() {
        return Map.copyOf(params);
    }

    /** Path inside the application to open. */
    public Optional<String> link() {
        return Optional.ofNullable(link);
    }

    /** The site concerned, if any. */
    public Optional<UUID> siteId() {
        return Optional.ofNullable(siteId);
    }

    /** Whether it was read. */
    public boolean read() {
        return readAt != null;
    }

    /** When it was created. */
    public Instant createdAt() {
        return createdAt;
    }
}
