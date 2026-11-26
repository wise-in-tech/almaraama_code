package com.almaraama.workflow.application.guards;

import com.almaraama.shared.actor.ActorType;
import com.almaraama.shared.error.BusinessException;
import com.almaraama.shared.error.ErrorCategory;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** The conditions of the P1 catalogue (ADR-036), one Spring component each. */
public final class Guards {

    private Guards() {
    }

    /** The strings of a list in a configuration. */
    static List<String> strings(Object value) {
        return value instanceof Collection<?> c ? c.stream().map(String::valueOf).toList() : List.of();
    }

    /** Reason in the configured list (H5); comment for the reasons in {@code commentRequiredFor}. */
    @Component
    public static class ReasonRequired implements Guard {

        /** {@inheritDoc} */
        @Override
        public String code() {
            return "ReasonRequired";
        }

        /** {@code REASON_REQUIRED} for a missing or unlisted reason, {@code COMMENT_REQUIRED} when its comment is missing. */
        @Override
        public void check(GuardCall call) {
            List<String> reasons = strings(call.config().get("reasons"));
            String reason = call.input().reason().orElse(null);
            if (reason == null || !reasons.contains(reason)) {
                throw new BusinessException("REASON_REQUIRED", ErrorCategory.RULE_VIOLATION, "Reason of the list");
            }
            boolean needsComment = strings(call.config().get("commentRequiredFor")).contains(reason);
            if (needsComment && call.input().comment().map(String::isBlank).orElse(true)) {
                throw new BusinessException("COMMENT_REQUIRED", ErrorCategory.RULE_VIOLATION, "Comment needed");
            }
        }

        /** A non-empty list of reasons. */
        @Override
        public boolean validConfig(Map<String, Object> config) {
            return !strings(config.get("reasons")).isEmpty();
        }
    }

    /** A comment is mandatory (H6: cancellation by the manager). */
    @Component
    public static class CommentRequired implements Guard {

        /** {@inheritDoc} */
        @Override
        public String code() {
            return "CommentRequired";
        }

        /** {@code COMMENT_REQUIRED} when the comment is blank. */
        @Override
        public void check(GuardCall call) {
            if (call.input().comment().map(String::isBlank).orElse(true)) {
                throw new BusinessException("COMMENT_REQUIRED", ErrorCategory.RULE_VIOLATION, "Comment needed");
            }
        }
    }

    /** Only the requester of the subject (H6, reopen by the requester, requester validation). */
    @Component
    public static class IsRequester implements Guard {

        /** {@inheritDoc} */
        @Override
        public String code() {
            return "IsRequester";
        }

        /** Offered to the requester only. */
        @Override
        public boolean allows(GuardCall call) {
            return call.context().actorIsRequester();
        }

        /** {@code TRANSITION_NOT_AVAILABLE} for anyone else. */
        @Override
        public void check(GuardCall call) {
            if (!allows(call)) {
                throw notAvailable();
            }
        }
    }

    /** An option of the client is on (H5), named by {@code config.option}. */
    @Component
    public static class OptionEnabled implements Guard {

        /** {@inheritDoc} */
        @Override
        public String code() {
            return "OptionEnabled";
        }

        /** Offered only while the option is on. */
        @Override
        public boolean allows(GuardCall call) {
            return call.settings().enabled(String.valueOf(call.config().get("option")));
        }

        /** {@code TRANSITION_NOT_AVAILABLE} while the option is off. */
        @Override
        public void check(GuardCall call) {
            if (!allows(call)) {
                throw notAvailable();
            }
        }

        /** A known option. */
        @Override
        public boolean validConfig(Map<String, Object> config) {
            Object option = config.get("option");
            return "requesterValidation".equals(option) || "autoClose".equals(option);
        }
    }

    /**
     * With the requester validation on (H5) and a person as requester, the requester confirmed since the subject last
     * entered its step.
     */
    @Component
    public static class RequesterConfirmation implements Guard {

        /** {@inheritDoc} */
        @Override
        public String code() {
            return "RequesterConfirmation";
        }

        /** {@code REQUESTER_CONFIRMATION_REQUIRED} until a {@code confirm_resolution} entry exists. */
        @Override
        public void check(GuardCall call) {
            if (!call.settings().requesterValidation() || !call.context().requesterIsPerson()) {
                return;
            }
            boolean confirmed = call.history().get().stream()
                    .anyMatch(e -> e.transitionCode().filter("confirm_resolution"::equals).isPresent()
                            && !e.occurredAt().isBefore(call.enteredAt()));
            if (!confirmed) {
                throw new BusinessException("REQUESTER_CONFIRMATION_REQUIRED", ErrorCategory.RULE_VIOLATION,
                        "The requester must confirm first");
            }
        }
    }

    /** Only the system (automatic passages: auto-close). */
    @Component
    public static class SystemOnly implements Guard {

        /** {@inheritDoc} */
        @Override
        public String code() {
            return "SystemOnly";
        }

        /** Never offered to a person. */
        @Override
        public boolean allows(GuardCall call) {
            return call.context().actor().type() == ActorType.SYSTEM;
        }

        /** {@code TRANSITION_NOT_AVAILABLE} for a person. */
        @Override
        public void check(GuardCall call) {
            if (!allows(call)) {
                throw notAvailable();
            }
        }
    }

    /** The error of a passage not open to the actor. */
    static BusinessException notAvailable() {
        return new BusinessException("TRANSITION_NOT_AVAILABLE", ErrorCategory.CONFLICT, "Passage not open");
    }
}
