package com.sacco.mvp.domain;

public enum OtpRequirementMode {
    LOGIN_MFA_ONLY,
    APPROVAL_ONLY,
    LOGIN_MFA_AND_APPROVAL;

    public boolean requiresLoginMfa() {
        return this == LOGIN_MFA_ONLY || this == LOGIN_MFA_AND_APPROVAL;
    }

    public boolean requiresApprovalOtp() {
        return this == APPROVAL_ONLY || this == LOGIN_MFA_AND_APPROVAL;
    }
}
