package com.sacco.mvp.web.form;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MemberRegistrationForm {
    @Size(max = 50, message = "{payroll.employeeNumber.invalid}")
    @Pattern(regexp = "^[A-Za-z0-9/._ -]*$", message = "{payroll.employeeNumber.invalid}")
    private String employeeNumber;

    @Email(message = "Enter a valid email address.")
    @NotBlank(message = "Enter your email address.")
    private String email;

    @NotBlank(message = "Enter your phone number.")
    @Pattern(regexp = "^255[0-9]{9}$", message = "Enter your phone number in the format 255XXXXXXXXX.")
    private String phone;

    @NotBlank(message = "{registration.firstName.required}")
    @Pattern(regexp = "\\s*\\S+\\s*", message = "{registration.name.single}")
    @Size(max = 80, message = "{registration.name.length}")
    private String firstName;

    @NotBlank(message = "{registration.middleName.required}")
    @Pattern(regexp = "\\s*\\S+\\s*", message = "{registration.name.single}")
    @Size(max = 80, message = "{registration.name.length}")
    private String middleName;

    @NotBlank(message = "{registration.lastName.required}")
    @Pattern(regexp = "\\s*\\S+\\s*", message = "{registration.name.single}")
    @Size(max = 80, message = "{registration.name.length}")
    private String lastName;

    @NotBlank(message = "Select a SACCO ID.")
    private String saccoId;

    @NotBlank(message = "Select a station ID.")
    private String stationId;

    @NotBlank(message = "Enter your password.")
    @Size(min = 8, message = "Password must be at least 8 characters.")
    private String password;

    @NotBlank(message = "Confirm your password.")
    private String confirmPassword;

    private String otpCode;

    /** A derived view of the three public fields; a submitted fullName cannot override them. */
    public String getFullName() {
        return java.util.stream.Stream.of(firstName, middleName, lastName)
            .map(value -> value == null ? "" : value.strip()).collect(java.util.stream.Collectors.joining(" "));
    }
}
