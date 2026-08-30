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
    @NotBlank(message = "Enter your member number.")
    private String memberNo;

    @Email(message = "Enter a valid email address.")
    @NotBlank(message = "Enter your email address.")
    private String email;

    @NotBlank(message = "Enter your phone number.")
    @Pattern(regexp = "^255[0-9]{9}$", message = "Enter your phone number in the format 255XXXXXXXXX.")
    private String phone;

    @NotBlank(message = "Enter your full names.")
    @Pattern(regexp = "^\\s*\\S+(?:\\s+\\S+){2}\\s*$", message = "Enter exactly three names.")
    private String fullName;

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
}
