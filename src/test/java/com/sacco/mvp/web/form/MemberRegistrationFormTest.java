package com.sacco.mvp.web.form;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class MemberRegistrationFormTest {
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test void fullNameIsDerivedFromThreeSeparateNames() {
        var form = new MemberRegistrationForm();form.setFirstName("  Asha ");form.setMiddleName(" Neema ");form.setLastName(" Mushi  ");
        assertThat(form.getFullName()).isEqualTo("Asha Neema Mushi");assertThat(nameErrors(form)).isEmpty();
    }
    @Test void everyNameIsRequired() {
        assertThat(nameErrors(new MemberRegistrationForm())).contains("firstName","middleName","lastName");
    }
    @Test void multipleNamesCannotBeEnteredInOnePart() {
        var form = new MemberRegistrationForm();form.setFirstName("Asha Neema");form.setMiddleName("Maria");form.setLastName("Mushi");
        assertThat(nameErrors(form)).containsExactly("firstName");
    }
    @Test void namesFitTheExistingStoredFullName() {
        var form = new MemberRegistrationForm();form.setFirstName("a".repeat(81));form.setMiddleName("Neema");form.setLastName("Mushi");
        assertThat(nameErrors(form)).containsExactly("firstName");
    }
    @Test void publicRegistrationCannotBindAClientNumberOrOverrideTheDerivedName() {
        var form = new MemberRegistrationForm();
        var binder = new org.springframework.web.bind.WebDataBinder(form);
        binder.bind(new org.springframework.beans.MutablePropertyValues(java.util.Map.of("memberNo","CHOSEN-BY-CLIENT","fullName","Injected Full Name","firstName","Asha","middleName","Neema","lastName","Mushi","email","client@example.invalid")));
        assertThat(form.getEmail()).isEqualTo("client@example.invalid");assertThat(form.getFullName()).isEqualTo("Asha Neema Mushi");
        assertThat(java.util.Arrays.stream(MemberRegistrationForm.class.getDeclaredFields()).map(java.lang.reflect.Field::getName)).doesNotContain("memberNo","fullName");
    }
    private java.util.List<String> nameErrors(MemberRegistrationForm form) {
        return validator.validate(form).stream().map(v -> v.getPropertyPath().toString()).filter(name -> name.endsWith("Name")).distinct().sorted().toList();
    }
}
