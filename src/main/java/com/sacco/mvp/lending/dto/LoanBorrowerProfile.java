package com.sacco.mvp.lending.dto;

import java.util.UUID;

/** A borrower is either a person or a scoped group; a group has no personal client identity. */
public record LoanBorrowerProfile(UUID partyId, String kind, UUID clientId, UUID groupId,
                                  String number, String name, String email, String phone,
                                  String branch, String legalForm) {
    public boolean person() { return "CLIENT".equals(kind); }
    public boolean isPerson() { return person(); }
}
