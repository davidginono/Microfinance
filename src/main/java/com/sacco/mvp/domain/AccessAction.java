package com.sacco.mvp.domain;

public enum AccessAction {
    VIEW("View"),
    ADD("Add"),
    CREATE("Create"),
    UPDATE("Update"),
    APPROVE("Approve"),
    REJECT("Reject"),
    ASSIGN("Assign"),
    CONFIGURE("Configure"),
    EXPORT("Export"),
    DISBURSE("Disburse"),
    REVERSE("Reverse"),
    MANAGE("Manage"),
    DRAFT("Draft"),
    POST("Post"),
    PUBLISH("Publish"),
    RUN("Run"),
    DELETE("Delete");

    private final String displayName;

    AccessAction(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
