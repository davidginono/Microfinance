package com.sacco.mvp.web.form;

import lombok.Data;

/** Staff registration/contact command. Client profiles own field validation. */
@Data
public class MemberRegistrationForm {
    private String employeeNumber;
    private String email;
    private String phone;
    private String saccoId;
    private String stationId;
}
