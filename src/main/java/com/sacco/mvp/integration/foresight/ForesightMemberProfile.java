package com.sacco.mvp.integration.foresight;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ForesightMemberProfile(
    String surname,
    String otherName,
    String memberNo,
    String stationId,
    String saccoName,
    String phoneNumber,
    String email
) {
    public ForesightMemberProfile(String surname,
                                  String otherName,
                                  String memberNo,
                                  String stationId,
                                  String saccoName) {
        this(surname, otherName, memberNo, stationId, saccoName, null, null);
    }
}
