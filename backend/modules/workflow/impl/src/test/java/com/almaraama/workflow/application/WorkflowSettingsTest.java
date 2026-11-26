package com.almaraama.workflow.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.almaraama.shared.i18n.LocalizedText;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** H5, ADR-036: the options of a client survive their storage as JSON text. */
class WorkflowSettingsTest {

    @Test
    void H5_theOptionsOfAClientAreStoredAndReadBack() {
        WorkflowSettings settings = new WorkflowSettings(Optional.of(5), true,
                Map.of("RESOLVED", new LocalizedText(null, "Done", "Terminé")));
        WorkflowSettings read = WorkflowSettings.parse(settings.toJson());
        assertThat(read.autoCloseAfterDays()).contains(5);
        assertThat(read.requesterValidation()).isTrue();
        assertThat(read.labels().get("RESOLVED").fr()).isEqualTo("Terminé");
        assertThat(read.labels().get("RESOLVED").ar()).isNull();
        assertThat(read.enabled("requesterValidation")).isTrue();
        assertThat(read.enabled("autoClose")).isTrue();
    }

    @Test
    void H5_withoutOptionsNothingIsAutomatic() {
        WorkflowSettings none = WorkflowSettings.parse("{}");
        assertThat(none.autoCloseAfterDays()).isEmpty();
        assertThat(none.requesterValidation()).isFalse();
        assertThat(WorkflowSettings.parse("not json").labels()).isEmpty();
    }
}
