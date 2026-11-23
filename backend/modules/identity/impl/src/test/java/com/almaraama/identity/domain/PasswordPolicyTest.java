package com.almaraama.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.almaraama.shared.error.BusinessException;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Rules of a chosen password (D5, ADR-030, ASVS V2.1). */
class PasswordPolicyTest {

    private final PasswordPolicy policy = new PasswordPolicy(Set.of("password123", "azertyuiop")::contains);
    private final EmailAddress email = EmailAddress.parse("amina.benali@example.dz");

    @Test
    void D5_eightCharactersAtLeast() {
        assertRefused("Court7!", "PASSWORD_TOO_SHORT");
        assertThat(policy.check("huit car", email)).isEqualTo("huit car");
    }

    @Test
    void D5_atMost128CharactersCountedAsCodePoints() {
        assertThat(policy.check("ب".repeat(128), email)).hasSize(128);
        assertRefused("a".repeat(129), "PASSWORD_TOO_LONG");
    }

    @Test
    void ADR030_commonPasswordsAreRefusedWhateverTheirCase() {
        assertRefused("PassWord123", "PASSWORD_TOO_COMMON");
        assertRefused("AZERTYUIOP", "PASSWORD_TOO_COMMON");
    }

    @Test
    void ADR030_thePasswordIsNeverTheEmailOrItsLocalPart() {
        assertRefused("Amina.Benali@example.dz", "PASSWORD_SAME_AS_EMAIL");
        assertRefused("amina.benali", "PASSWORD_SAME_AS_EMAIL");
    }

    @Test
    void ADR030_noCompositionRuleAndUnicodeIsNormalized() {
        assertThat(policy.check("une phrase toute simple", email)).isEqualTo("une phrase toute simple");
        assertThat(PasswordPolicy.normalize("ﬁﬁﬁﬁ café")).isEqualTo("fifififi café");
    }

    /** The password is refused with this code. */
    private void assertRefused(String password, String code) {
        assertThatThrownBy(() -> policy.check(password, email)).isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).code()).isEqualTo(code);
    }
}
