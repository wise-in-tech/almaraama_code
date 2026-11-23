-- DEVELOPMENT AND TEST DATA ONLY (location db/dev, enabled by the dev profile and the tests).
-- Reference lists are not blocking (decision of 20 November): the official wilayas and communes, typologies and
-- categories will be loaded by the architect directly in the tables, in the format documented in the sites
-- migration V202611220002. Until then, a few zones and the usual unit categories, for development and tests.
-- The file keeps its former name (it used to fill sites.city) so that existing development databases migrate.

-- ---- zones (A8): wilaya (level 1) then commune (level 2); parent_id, level and path are filled by the trigger ----
INSERT INTO sites.zone (country_code, code, parent_code, name_ar, name_en, name_fr)
VALUES ('DZ', '16', NULL, 'الجزائر', 'Algiers', 'Alger'),
       ('DZ', '31', NULL, 'وهران', 'Oran', 'Oran'),
       ('DZ', '25', NULL, 'قسنطينة', 'Constantine', 'Constantine'),
       ('DZ', '09', NULL, 'البليدة', 'Blida', 'Blida'),
       ('DZ', '15', NULL, 'تيزي وزو', 'Tizi Ouzou', 'Tizi Ouzou'),
       ('DZ', '19', NULL, 'سطيف', 'Setif', 'Sétif'),
       ('DZ', '06', NULL, 'بجاية', 'Bejaia', 'Béjaïa'),
       ('DZ', '23', NULL, 'عنابة', 'Annaba', 'Annaba')
ON CONFLICT (country_code, code) DO UPDATE
    SET name_ar = EXCLUDED.name_ar, name_en = EXCLUDED.name_en, name_fr = EXCLUDED.name_fr;

INSERT INTO sites.zone (country_code, code, parent_code, name_ar, name_en, name_fr)
VALUES ('DZ', '1601', '16', 'الجزائر الوسطى', 'Algiers Centre', 'Alger-Centre'),
       ('DZ', '1602', '16', 'سيدي امحمد', 'Sidi M''Hamed', 'Sidi M''Hamed'),
       ('DZ', '1621', '16', 'بئر مراد رايس', 'Bir Mourad Raïs', 'Bir Mourad Raïs'),
       ('DZ', '3101', '31', 'وهران', 'Oran', 'Oran'),
       ('DZ', '2501', '25', 'قسنطينة', 'Constantine', 'Constantine'),
       ('DZ', '0901', '09', 'البليدة', 'Blida', 'Blida'),
       ('DZ', '1501', '15', 'تيزي وزو', 'Tizi Ouzou', 'Tizi Ouzou'),
       ('DZ', '1901', '19', 'سطيف', 'Setif', 'Sétif'),
       ('DZ', '0601', '06', 'بجاية', 'Bejaia', 'Béjaïa'),
       ('DZ', '2301', '23', 'عنابة', 'Annaba', 'Annaba')
ON CONFLICT (country_code, code) DO UPDATE
    SET name_ar = EXCLUDED.name_ar, name_en = EXCLUDED.name_en, name_fr = EXCLUDED.name_fr;

-- ---- default unit categories (A6, criterion 2): typologies of apartments, a few categories for the other types ----
INSERT INTO sites.unit_category (unit_type, code, name_ar, name_en, name_fr, sort_order)
SELECT v.unit_type, v.code, v.name_ar, v.name_en, v.name_fr, v.sort_order
FROM (VALUES ('APARTMENT', 'STUDIO', 'استوديو', 'Studio', 'Studio', 10),
             ('APARTMENT', 'T1', 'F1', 'One-room (T1)', 'T1', 20),
             ('APARTMENT', 'T2', 'F2', 'Two-room (T2)', 'T2', 30),
             ('APARTMENT', 'T3', 'F3', 'Three-room (T3)', 'T3', 40),
             ('APARTMENT', 'T4', 'F4', 'Four-room (T4)', 'T4', 50),
             ('APARTMENT', 'T5', 'F5', 'Five-room (T5)', 'T5', 60),
             ('APARTMENT', 'DUPLEX', 'دوبلكس', 'Duplex', 'Duplex', 70),
             ('SHOP', 'SMALL_SHOP', 'محل صغير', 'Small shop', 'Petit magasin', 10),
             ('SHOP', 'LARGE_SHOP', 'محل كبير', 'Large shop', 'Grand magasin', 20),
             ('PARKING', 'COVERED', 'موقف مغطى', 'Covered parking', 'Parking couvert', 10),
             ('PARKING', 'OUTDOOR', 'موقف خارجي', 'Outdoor parking', 'Parking extérieur', 20),
             ('STORAGE', 'CELLAR', 'قبو', 'Cellar', 'Cave', 10)) AS v(unit_type, code, name_ar, name_en, name_fr,
                                                                       sort_order)
WHERE NOT EXISTS (SELECT 1 FROM sites.unit_category c
                  WHERE c.scope_type = 'PLATFORM' AND c.unit_type = v.unit_type AND c.code = v.code);
