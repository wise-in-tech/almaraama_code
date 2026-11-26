-- DEVELOPMENT AND TEST DATA ONLY (location db/dev, enabled by the dev profile and the tests).
-- The official lists of equipment families and models and of issue categories and types will be loaded by the
-- architect in the tables (decision of 20 November), in the format documented in the assets migration V202611260003
-- and the maintenance migration V202611260006. Until then, a few defaults for development and tests.

-- ---- equipment families and models (G1) ----
INSERT INTO assets.asset_family (id, owner_scope, code, name_ar, name_en, name_fr, sort_order)
VALUES ('0194b000-0000-7000-8000-000000000001', 'PLATFORM', 'ELEVATOR', 'مصعد', 'Elevator', 'Ascenseur', 10),
       ('0194b000-0000-7000-8000-000000000002', 'PLATFORM', 'FIRE_EXTINGUISHER', 'مطفأة حريق', 'Fire extinguisher',
        'Extincteur', 20),
       ('0194b000-0000-7000-8000-000000000003', 'PLATFORM', 'GATE', 'بوابة', 'Gate', 'Portail', 30)
ON CONFLICT (id) DO NOTHING;

INSERT INTO assets.asset_model (id, family_id, owner_scope, name, model_version, manufacturer)
VALUES ('0194b000-0000-7000-8000-000000000011', '0194b000-0000-7000-8000-000000000001', 'PLATFORM', 'Gen2',
        '2020', 'Otis'),
       ('0194b000-0000-7000-8000-000000000012', '0194b000-0000-7000-8000-000000000002', 'PLATFORM', 'P6',
        '6 kg', NULL)
ON CONFLICT (id) DO NOTHING;

-- ---- characteristics of the elevator (G3) ----
INSERT INTO customization.attribute_definition (id, owner_scope, family_id, code, data_type, required, label_ar,
                                                label_en, label_fr, unit, sort_order)
VALUES ('0194b000-0000-7000-8000-000000000021', 'PLATFORM', '0194b000-0000-7000-8000-000000000001', 'capacityKg',
        'INTEGER', FALSE, 'الحمولة', 'Capacity', 'Charge', 'kg', 10),
       ('0194b000-0000-7000-8000-000000000022', 'PLATFORM', '0194b000-0000-7000-8000-000000000001', 'doorType',
        'LIST', FALSE, 'نوع الباب', 'Door type', 'Type de porte', NULL, 20)
ON CONFLICT (id) DO NOTHING;
INSERT INTO customization.attribute_option (id, definition_id, code, label_ar, label_en, label_fr, sort_order)
VALUES ('0194b000-0000-7000-8000-000000000031', '0194b000-0000-7000-8000-000000000022', 'AUTOMATIC', 'آلي',
        'Automatic', 'Automatique', 10),
       ('0194b000-0000-7000-8000-000000000032', '0194b000-0000-7000-8000-000000000022', 'MANUAL', 'يدوي', 'Manual',
        'Manuelle', 20)
ON CONFLICT (id) DO NOTHING;

-- ---- issue categories and types (H2, H3, H8) ----
INSERT INTO maintenance.issue_category (id, owner_scope, code, name_ar, name_en, name_fr, sort_order)
VALUES ('0194b000-0000-7000-8000-000000000041', 'PLATFORM', 'TECHNICAL', 'تقني', 'Technical', 'Technique', 10),
       ('0194b000-0000-7000-8000-000000000042', 'PLATFORM', 'CLEANING', 'نظافة', 'Cleaning', 'Nettoyage', 20),
       ('0194b000-0000-7000-8000-000000000043', 'PLATFORM', 'SECURITY', 'أمن', 'Security', 'Sécurité', 30)
ON CONFLICT (id) DO NOTHING;

INSERT INTO maintenance.issue_type (id, category_id, owner_scope, code, name_ar, name_en, name_fr, asset_required,
                                    default_visibility, asset_family_ids, sort_order)
VALUES ('0194b000-0000-7000-8000-000000000051', '0194b000-0000-7000-8000-000000000041', 'PLATFORM', 'ELEVATOR_STUCK',
        'مصعد معطل', 'Elevator stuck', 'Ascenseur bloqué', TRUE, 'COMMON', '0194b000-0000-7000-8000-000000000001', 10),
       ('0194b000-0000-7000-8000-000000000052', '0194b000-0000-7000-8000-000000000041', 'PLATFORM', 'WATER_LEAK',
        'تسرب مياه', 'Water leak', 'Fuite d''eau', FALSE, 'PRIVATE', '', 20),
       ('0194b000-0000-7000-8000-000000000053', '0194b000-0000-7000-8000-000000000042', 'PLATFORM', 'DIRTY_STAIRS',
        'درج متسخ', 'Dirty stairs', 'Escalier sale', FALSE, 'COMMON', '', 30),
       ('0194b000-0000-7000-8000-000000000054', '0194b000-0000-7000-8000-000000000043', 'PLATFORM', 'NOISE',
        'إزعاج', 'Noise', 'Nuisance sonore', FALSE, 'PRIVATE', '', 40)
ON CONFLICT (id) DO NOTHING;
