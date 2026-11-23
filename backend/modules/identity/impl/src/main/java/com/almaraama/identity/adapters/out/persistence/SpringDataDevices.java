package com.almaraama.identity.adapters.out.persistence;

import com.almaraama.identity.domain.TrustedDevice;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.Repository;

/** Spring Data repository of trusted devices, schema identity. Extends {@link Repository}: no delete method exists (M2). */
interface SpringDataDevices extends Repository<TrustedDevice, UUID> {
    /** Loads a device. */
    Optional<TrustedDevice> findById(UUID id);

    /** The devices of a person whose session is not ended. */
    List<TrustedDevice> findByPersonIdAndRevokedAtIsNull(UUID personId);

    /** Inserts or updates a device. */
    TrustedDevice save(TrustedDevice device);
}
