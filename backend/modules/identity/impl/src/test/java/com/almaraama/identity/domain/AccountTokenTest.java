package com.almaraama.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.almaraama.identity.api.events.AccountEmailKind;
import com.almaraama.identity.api.AccountStatus;
import com.almaraama.shared.actor.Actor;
import com.almaraama.shared.i18n.Language;
import com.almaraama.shared.id.PersonId;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/** Account links (ADR-030) and e-mail verification (D1, D4). */
class AccountTokenTest {

    private final Instant now = Instant.parse("2026-11-22T10:00:00Z");

    @Test
    void ADR030_aLinkWorksOnceAndExpires() {
        AccountToken token = AccountToken.issue(PersonId.newId(), AccountEmailKind.PASSWORD_RESET, "hash",
                Actor.platform(), now, Duration.ofHours(1));
        assertThat(token.isUsable(now.plus(Duration.ofMinutes(59)))).isTrue();
        assertThat(token.isUsable(now.plus(Duration.ofHours(1)))).isFalse();
        token.markUsed(now);
        assertThatThrownBy(() -> token.markUsed(now)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void ADR030_aNewLinkRevokesThePreviousOne() {
        AccountToken token = AccountToken.issue(PersonId.newId(), AccountEmailKind.SET_PASSWORD, "hash",
                Actor.platform(), now, Duration.ofDays(7));
        token.revoke(now);
        assertThat(token.isUsable(now)).isFalse();
    }

    @Test
    void ADR030_aPasswordChangedNoticeCarriesNoLink() {
        assertThatThrownBy(() -> AccountToken.issue(PersonId.newId(), AccountEmailKind.PASSWORD_CHANGED, "hash",
                Actor.platform(), now, Duration.ZERO)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void D4_settingThePasswordVerifiesTheEmailAndActivatesTheAccount() {
        Person person = Person.invite("Amina", "Benali", EmailAddress.parse("amina@example.dz"),
                new PhoneNumber("+213661234567"), Language.AR, now);
        assertThat(person.verifyEmail(now)).isTrue();
        assertThat(person.status()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(person.emailVerifiedAt()).contains(now);
        assertThat(person.verifyEmail(now.plusSeconds(1))).isFalse();
    }
}
