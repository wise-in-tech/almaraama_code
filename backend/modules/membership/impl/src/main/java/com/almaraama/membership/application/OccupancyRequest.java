package com.almaraama.membership.application;

import com.almaraama.membership.domain.OccupancyRole;
import com.almaraama.shared.id.UnitId;
import java.time.LocalDate;

/** An occupancy to start, as entered by a manager (E1 to E5). {@code startedOn} defaults to today. */
public record OccupancyRequest(UnitId unitId, OccupancyRole role, boolean primaryTenant, LocalDate startedOn) {
}
