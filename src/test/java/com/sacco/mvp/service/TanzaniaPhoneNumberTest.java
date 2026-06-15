package com.sacco.mvp.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TanzaniaPhoneNumberTest {

    @Test
    void normalizesSupportedTanzaniaFormatsToCountryCodeDigits() {
        assertThat(TanzaniaPhoneNumber.normalizeRequired("0712 345 678")).isEqualTo("255712345678");
        assertThat(TanzaniaPhoneNumber.normalizeRequired("+255712345678")).isEqualTo("255712345678");
        assertThat(TanzaniaPhoneNumber.normalizeRequired("255712345678")).isEqualTo("255712345678");
    }

    @Test
    void rejectsNumbersOutsideTheCanonicalTanzaniaLength() {
        assertThatThrownBy(() -> TanzaniaPhoneNumber.normalizeRequired("712345678"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Enter a valid phone number in the format 255XXXXXXXXX.");
    }
}
