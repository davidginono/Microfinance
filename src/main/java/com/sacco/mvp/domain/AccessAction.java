package com.sacco.mvp.domain;

public enum AccessAction {
    VIEW("View"),
    VALIDATE("Validate"), POST("Post"), INITIATE("Initiate"), WITHDRAW("Withdraw"), HOLD("Hold"), FORCE_APPROVE("Force approve"), ESCALATED_APPROVE("Escalated approval"),
    ADD("Add"),
    CREATE("Create"),
    UPDATE("Update"),
    APPROVE("Approve"),
    WAIVE("Waive interest"),
    REJECT("Reject"),
    ASSIGN("Assign"),
    CONFIGURE("Configure"),
    EXPORT("Export"),
    IMPORT("Import"),
    DISBURSE("Disburse"),
    REVERSE("Reverse"),
    DELETE("Delete");

    private final String displayName;

    AccessAction(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
