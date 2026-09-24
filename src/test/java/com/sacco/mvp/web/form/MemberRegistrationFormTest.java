package com.sacco.mvp.web.form;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MemberRegistrationFormTest {
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void fullNameAcceptsExactlyThreeNamesWithFlexibleWhitespace() {
        MemberRegistrationForm form = new MemberRegistrationForm();
        form.setFullName("  Asha   Neema Mushi  ");

        assertThat(fullNameErrors(form)).isEmpty();
    }

    @Test
    void fullNameRejectsFewerThanThreeNames() {
        MemberRegistrationForm form = new MemberRegistrationForm();
        form.setFullName("Asha Mushi");

        assertThat(fullNameErrors(form)).containsExactly("Enter exactly three names.");
    }

    @Test
    void fullNameRejectsMoreThanThreeNames() {
        MemberRegistrationForm form = new MemberRegistrationForm();
        form.setFullName("Asha Neema Maria Mushi");

        assertThat(fullNameErrors(form)).containsExactly("Enter exactly three names.");
    }

    private java.util.List<String> fullNameErrors(MemberRegistrationForm form) {
        return validator.validate(form).stream()
            .filter(violation -> "fullName".equals(violation.getPropertyPath().toString()))
            .map(violation -> violation.getMessage())
            .toList();
    }
}
