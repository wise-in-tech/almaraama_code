-- Sprint 3 (RB-6, C11, ADR-026, ADR-037): default permissions of equipment, issue types, tickets and the ticket
-- circuit back-office, from section 3.3 of the specification. The ◐ of section 3.3 ("his own", "his units", "common
-- parts", "assigned") are relationships checked in the queries, not permissions: the resident therefore receives no
-- ticket:read, and still sees his tickets and the common tickets of his residence. Differences already made by an
-- organization stay applied (C11). The promoteur (grants_all) is not listed: he holds every permission.
INSERT INTO membership.role_default_permission (role_code, permission)
VALUES ('SITE_MANAGER', 'asset:create'),
       ('SITE_MANAGER', 'asset:read'),
       ('SITE_MANAGER', 'asset:update'),
       ('SITE_MANAGER', 'asset:archive'),
       ('SITE_MANAGER', 'issue-type:read'),
       ('SITE_MANAGER', 'issue-type:manage'),
       ('SITE_MANAGER', 'ticket:create'),
       ('SITE_MANAGER', 'ticket:read'),
       ('SITE_MANAGER', 'ticket:read-private'),
       ('SITE_MANAGER', 'ticket:update-status'),
       ('SITE_MANAGER', 'ticket:cancel'),
       ('SITE_MANAGER', 'ticket:close'),
       ('SITE_MANAGER', 'ticket:reopen'),
       ('TECHNICIAN', 'issue-type:read'),
       ('TECHNICIAN', 'ticket:create'),
       ('TECHNICIAN', 'ticket:update-status'),
       ('RESIDENT', 'asset:read'),
       ('RESIDENT', 'issue-type:read'),
       ('RESIDENT', 'ticket:create'),
       ('PLATFORM_ADMIN', 'ticket-workflow:read'),
       ('PLATFORM_ADMIN', 'ticket-workflow:configure');
