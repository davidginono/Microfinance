package com.sacco.mvp.web.form;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MinorAdminRegistrationForm {
    private String saccoId;
    private String stationId;
    private String memberNo;
    private String fullName;
    private String email;
    private String phone;
    private String signatureText;
}
