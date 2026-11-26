package com.almaraama.workflow.api;

import java.util.UUID;

/** An automatic passage due now (H5 auto-close): the calling module takes it through its own use case. */
public record DueTransition(UUID instanceId, String subjectType, UUID subjectId, String transitionCode) {
}
