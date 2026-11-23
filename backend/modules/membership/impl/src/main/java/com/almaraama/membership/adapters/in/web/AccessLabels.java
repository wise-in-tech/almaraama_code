package com.almaraama.membership.adapters.in.web;

import com.almaraama.membership.adapters.in.web.MembershipWebDtos.LocalizedText;
import com.almaraama.membership.domain.Role;
import com.almaraama.shared.access.Permission;
import java.util.Map;

/**
 * Labels of the roles and permissions in the interface languages (M1), for the catalogue endpoints. A permission's
 * label is built from its action and resource ({@code unit:import} gives "Import units"); role names come from the
 * role table. Both move to the text dictionary once ADR-029 is validated.
 */
final class AccessLabels {

    /** Resource names (plural) in Arabic, English, French. */
    private static final Map<String, String[]> RESOURCES = Map.ofEntries(
            Map.entry("organization", new String[] {"المؤسسات", "organizations", "les organisations"}),
            Map.entry("site", new String[] {"الإقامات", "residences", "les résidences"}),
            Map.entry("building", new String[] {"العمارات", "buildings", "les bâtiments"}),
            Map.entry("outdoor-area", new String[] {"المساحات الخارجية", "outdoor areas", "les espaces extérieurs"}),
            Map.entry("zone", new String[] {"مناطق مؤقتة", "provisional zones", "des zones provisoires"}),
            Map.entry("registration-qr", new String[] {"رمز التسجيل", "the registration QR code",
                    "le QR code d'inscription"}),
            Map.entry("unit-category", new String[] {"فئات الأملاك", "unit categories", "les catégories de biens"}),
            Map.entry("unit", new String[] {"الأملاك", "units", "les biens"}),
            Map.entry("account", new String[] {"الحسابات", "accounts", "les comptes"}),
            Map.entry("access", new String[] {"طلبات الدخول", "access requests", "les demandes d'accès"}),
            Map.entry("role-assignment", new String[] {"الأدوار", "roles", "les rôles"}),
            Map.entry("permission-adjustment", new String[] {"الصلاحيات", "permissions", "les droits"}),
            Map.entry("occupancy", new String[] {"إسناد الأملاك", "unit assignments", "les attributions de biens"}));

    /** Action verbs in Arabic, English, French. */
    private static final Map<String, String[]> ACTIONS = Map.ofEntries(
            Map.entry("create", new String[] {"إنشاء", "Create", "Créer"}),
            Map.entry("read", new String[] {"عرض", "View", "Voir"}),
            Map.entry("update", new String[] {"تعديل", "Edit", "Modifier"}),
            Map.entry("archive", new String[] {"أرشفة", "Archive", "Archiver"}),
            Map.entry("deactivate", new String[] {"تعطيل", "Deactivate", "Désactiver"}),
            Map.entry("import", new String[] {"استيراد", "Import", "Importer"}),
            Map.entry("invite", new String[] {"دعوة أصحاب", "Invite the owners of", "Inviter les titulaires de"}),
            Map.entry("manage", new String[] {"إدارة", "Manage", "Gérer"}),
            Map.entry("create-provisional", new String[] {"إنشاء", "Create", "Créer"}),
            Map.entry("approve", new String[] {"قبول", "Approve", "Valider"}),
            Map.entry("refuse", new String[] {"رفض", "Refuse", "Refuser"}),
            Map.entry("restrict", new String[] {"تقييد", "Restrict", "Restreindre"}),
            Map.entry("revoke", new String[] {"سحب", "Revoke", "Retirer"}),
            Map.entry("grant", new String[] {"منح", "Grant", "Donner"}),
            Map.entry("end", new String[] {"إنهاء", "End", "Terminer"}));

    /** Static labels only. */
    private AccessLabels() {
    }

    /** Name of a role in the three languages, from its row. */
    static LocalizedText role(Role role) {
        return new LocalizedText(role.name().ar(), role.name().en(), role.name().fr());
    }

    /** Short label of a permission in the three languages: the action, then the resource. */
    static LocalizedText label(Permission permission) {
        String[] parts = permission.code().split(":");
        String[] resource = RESOURCES.getOrDefault(parts[0], new String[] {parts[0], parts[0], parts[0]});
        String[] action = ACTIONS.getOrDefault(parts[1], new String[] {parts[1], parts[1], parts[1]});
        return new LocalizedText(action[0] + " " + resource[0], action[1] + " " + resource[1],
                action[2] + " " + resource[2]);
    }

    /** One-sentence description: the label, and whether the promoteur may adjust it (C9). */
    static LocalizedText description(Permission permission) {
        LocalizedText label = label(permission);
        if (permission.adjustable()) {
            return new LocalizedText(label.ar() + ".", label.en() + ".", label.fr() + ".");
        }
        return new LocalizedText(label.ar() + ". لا يمكن تعديله.", label.en() + ". Cannot be adjusted.",
                label.fr() + ". Non ajustable.");
    }
}
