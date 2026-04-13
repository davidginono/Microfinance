package com.sacco.mvp.integration.foresight;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ForesightMemberProfile(
    String surname,
    String otherName,
    String memberNo,
    String stationId,
    String saccoName
) {
}
