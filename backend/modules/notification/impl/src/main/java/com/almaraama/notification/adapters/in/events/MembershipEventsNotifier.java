package com.almaraama.notification.adapters.in.events;

import com.almaraama.membership.api.events.MembershipApprovedV1;
import com.almaraama.membership.api.events.MembershipAutoApprovedV1;
import com.almaraama.membership.api.events.MembershipRejectedV1;
import com.almaraama.membership.api.events.MembershipRequestedV1;
import com.almaraama.notification.application.Notifications;
import com.almaraama.notification.domain.NotificationTemplate;
import com.almaraama.shared.id.PersonId;
import java.util.Map;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * K1: whoever may decide is notified of each access request and of each approval by a pre-registration key; the
 * person is notified of the decision (D2, D3).
 */
@Component
class MembershipEventsNotifier {

    private final Notifications notifications;

    /** @param notifications writes the notifications */
    MembershipEventsNotifier(Notifications notifications) {
        this.notifications = notifications;
    }

    /** K1: notifies each person who may decide on the site of the access request. */
    @ApplicationModuleListener
    void on(MembershipRequestedV1 event) {
        for (PersonId approver : event.approverIds()) {
            notifications.notify(approver, NotificationTemplate.ACCESS_REQUESTED,
                    Map.of("personName", event.personName(), "siteName", event.siteName()),
                    "/sites/" + event.siteId() + "/memberships?status=PENDING", event.siteId(),
                    "ACCESS_REQUESTED:" + event.membershipId() + ":" + approver);
        }
    }

    /** K1, D3: informs each person who may decide on the site that a pre-registration key approved an access. */
    @ApplicationModuleListener
    void on(MembershipAutoApprovedV1 event) {
        for (PersonId approver : event.approverIds()) {
            notifications.notify(approver, NotificationTemplate.ACCESS_AUTO_APPROVED,
                    Map.of("personName", event.personName(), "siteName", event.siteName()),
                    "/sites/" + event.siteId() + "/memberships?status=ACTIVE", event.siteId(),
                    "ACCESS_AUTO_APPROVED:" + event.membershipId() + ":" + approver);
        }
    }

    /** Tells the person their access was approved. */
    @ApplicationModuleListener
    void on(MembershipApprovedV1 event) {
        notifications.notify(event.personId(), NotificationTemplate.ACCESS_APPROVED,
                Map.of("siteName", event.siteName()), "/", event.siteId(),
                "ACCESS_APPROVED:" + event.membershipId());
    }

    /** Tells the person their access was rejected. */
    @ApplicationModuleListener
    void on(MembershipRejectedV1 event) {
        notifications.notify(event.personId(), NotificationTemplate.ACCESS_REJECTED,
                Map.of("siteName", event.siteName()), "/me", event.siteId(),
                "ACCESS_REJECTED:" + event.membershipId());
    }
}
