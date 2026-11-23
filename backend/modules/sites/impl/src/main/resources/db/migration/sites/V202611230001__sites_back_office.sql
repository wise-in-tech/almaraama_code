-- Sprint 2 (ADR-035): back-office of the platform administrators on the reference data and the organizations.
-- Nothing is deleted (M2): a merged provisional zone stays, DEACTIVATED, with the zone it was merged into; a
-- deactivated organization stays with all its data.

-- A8: a provisional zone merged into a validated one keeps a pointer to it, for the history.
ALTER TABLE sites.zone ADD COLUMN merged_into UUID REFERENCES sites.zone (id);
ALTER TABLE sites.zone ADD COLUMN updated_at TIMESTAMPTZ;

-- C12: when and why an organization was deactivated (the active flag already exists).
ALTER TABLE sites.organization ADD COLUMN deactivated_at TIMESTAMPTZ;
ALTER TABLE sites.organization ADD COLUMN deactivation_reason TEXT;
