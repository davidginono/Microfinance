package com.sacco.mvp.web.form;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
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

    @NotBlank(message = "Enter your full names.")
    private String fullName;

    @NotBlank(message = "Select a SACCO ID.")
    private String saccoId;

    @NotBlank(message = "Select a station ID.")
    private String stationId;

    @NotBlank(message = "Enter the signature you want to use on your loan forms.")
    @Pattern(
        regexp = "^[A-Z][a-z]+(?: [A-Z])?(?: [A-Z][a-z]+)+$",
        message = "Enter your signature like James M Juma."
    )
    private String signatureText;

    private String otpCode;
}
