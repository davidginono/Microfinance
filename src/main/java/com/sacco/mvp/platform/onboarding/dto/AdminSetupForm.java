package com.sacco.mvp.platform.onboarding.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AdminSetupForm {
    private String fullName="", email="", phone="";
    private String currentPassword="", password="", confirmPassword="";
}
