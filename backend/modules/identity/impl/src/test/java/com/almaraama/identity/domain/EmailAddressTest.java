package com.almaraama.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.almaraama.shared.error.BusinessException;
import org.junit.jupiter.api.Test;

/** The e-mail, sign-in identifier (D1, ADR-030). */
class EmailAddressTest {

    @Test
    void D1_anEmailIsTrimmedAndLowerCasedSoOneMailboxHasOneAccount() {
        assertThat(EmailAddress.parse("  Amina.Benali@Example.DZ ").value()).isEqualTo("amina.benali@example.dz");
    }

    @Test
    void D1_somethingThatIsNotAnEmailIsRefused() {
        assertThatThrownBy(() -> EmailAddress.parse("amina")).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> EmailAddress.parse("a b@example.dz")).isInstanceOf(BusinessException.class);
        assertThat(EmailAddress.tryParse("@example.dz")).isEmpty();
    }

    @Test
    void ADR033_onlyTheDomainMayBeLogged() {
        EmailAddress email = EmailAddress.parse("amina@example.dz");
        assertThat(email.domain()).isEqualTo("example.dz");
        assertThat(new EmailMessage(email, "s", "t", "h").toString()).doesNotContain("amina");
    }
}
