package com.sacco.mvp.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class StaffRegistrationNameTest {
    private final NameSignatureService names = new NameSignatureService();

    @ParameterizedTest
    @ValueSource(strings = {"  Asha   Jane Mushi ", "Asha-Jane Mary O'Neil", "Élodie Jane D’Angelo", "李 王 明"})
    void threeNamesSupportNormalNamePunctuationAndUnicode(String value) {
        assertThat(names.requireStaffFullName(value)).isEqualTo(value.strip().replaceAll("(?U)\\s+", " ").toUpperCase(java.util.Locale.ROOT));
    }

    @Test void newRegistrationDoesNotInvalidateHistoricalTwoNameSignatures() {
        assertThat(names.signatureFromFullName("Mary Manager")).isEqualTo("Mary M");
        assertThatThrownBy(() -> names.requireStaffFullName("A".repeat(115) + " Jane Mushi"))
            .hasMessage("staffRegistration.nameInvalid");
    }
}
