package com.almaraama.assets.application;

import com.almaraama.assets.domain.AssetRepositories.Suppliers;
import com.almaraama.assets.domain.Supplier;
import com.almaraama.shared.access.Permission;
import com.almaraama.shared.error.BusinessException;
import com.almaraama.shared.error.ErrorCategory;
import com.almaraama.shared.id.OrganizationId;
import com.almaraama.shared.perimeter.Perimeter;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Supplier contacts of an organization (G3): reusable on many equipment, managed with the equipment permissions in
 * the organization or one of its sites (management view, ADR-039). A supplier's number is often a landline, so it is
 * checked loosely and kept as typed, without spaces.
 */
@Service
public class SupplierService {

    private static final Pattern PHONE = Pattern.compile("^\\+?[0-9]{6,15}$");

    /** What a person types for a supplier. */
    public record SupplierCommand(String name, String phone, String email, String activity) {
    }

    private final Suppliers suppliers;
    private final Clock clock;

    /**
     * @param suppliers the supplier table
     * @param clock     time of changes
     */
    SupplierService(Suppliers suppliers, Clock clock) {
        this.suppliers = suppliers;
        this.clock = clock;
    }

    /** The organization's suppliers matching {@code query}; needs {@code asset:update} in it. */
    @Transactional(readOnly = true)
    public List<Supplier> list(Perimeter perimeter, OrganizationId organization, String query,
                               boolean includeInactive) {
        perimeter.requireInOrganization(Permission.ASSET_UPDATE, organization);
        return suppliers.ofOrganization(organization, query, includeInactive);
    }

    /** Adds a supplier; needs {@code asset:create} in the organization. {@code PHONE_INVALID}, {@code SUPPLIER_INVALID}. */
    @Transactional
    public Supplier create(Perimeter perimeter, OrganizationId organization, SupplierCommand command) {
        perimeter.requireInOrganization(Permission.ASSET_CREATE, organization);
        return suppliers.save(Supplier.create(organization, command.name(), phone(command.phone()), command.email(),
                command.activity(), clock.instant()));
    }

    /** Changes or deactivates a supplier; needs {@code asset:update} in its organization. {@code VERSION_CONFLICT}. */
    @Transactional
    public Supplier update(Perimeter perimeter, UUID supplierId, SupplierCommand command, boolean active,
                           long version) {
        Supplier supplier = suppliers.findById(supplierId).orElseThrow(Errors::notFound);
        perimeter.requireInOrganization(Permission.ASSET_UPDATE, supplier.organizationId());
        Errors.checkVersion(version, supplier.version());
        supplier.update(command.name(), phone(command.phone()), command.email(), command.activity(), active,
                clock.instant());
        return suppliers.save(supplier);
    }

    /** The number without spaces, dots, dashes or brackets; {@code PHONE_INVALID} when it is not 6 to 15 digits. */
    static String phone(String typed) {
        if (typed == null || typed.isBlank()) {
            return null;
        }
        String compact = typed.replaceAll("[\\s().-]", "");
        if (compact.startsWith("00")) {
            compact = "+" + compact.substring(2);
        }
        if (!PHONE.matcher(compact).matches()) {
            throw new BusinessException("PHONE_INVALID", ErrorCategory.RULE_VIOLATION, "Phone number");
        }
        return compact;
    }
}
